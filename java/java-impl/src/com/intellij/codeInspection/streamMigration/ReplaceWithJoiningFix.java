package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Frédéric Hannes
 */
class ReplaceWithJoiningFix extends MigrateToStreamFix {

  private boolean delimiter;

  public ReplaceWithJoiningFix(boolean delimiter) {
    this.delimiter = delimiter;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with joining()";
  }

  private static PsiElement replaceWithStringConcatenation(@NotNull Project project,
                                                           PsiLoopStatement loopStatement,
                                                           PsiVariable var,
                                                           PsiVariable checkVar,
                                                           StringBuilder builder,
                                                           PsiType expressionType) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    restoreComments(loopStatement, loopStatement.getBody());
    StreamApiMigrationInspection.InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(var, loopStatement);
    if (status != StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN) {
      // Get initializer constructor argument if present
      PsiExpression sbArg = StringBufferJoinHandling.getInitArgument(var, 0);
      if (sbArg != null) {
        builder.insert(0, " + ");
        builder.insert(0, sbArg.getText());
      }

      // Replace variable declaration type and initializer
      if (var.getTypeElement() != null) {
        if (checkVar != null) {
          checkVar.delete();
          // Refresh status after removing switch variable
          status = StreamApiMigrationInspection.getInitializerUsageStatus(var, loopStatement);
        }
        PsiExpression initializer = var.getInitializer();
        var.getTypeElement().replace(elementFactory.createTypeElement(expressionType));
        return replaceInitializer(loopStatement, var, initializer, builder.toString(), status);
      }
    }
    return loopStatement;
  }

  @Override
  PsiElement migrate(@NotNull Project project,
                     @NotNull PsiLoopStatement loopStatement,
                     @NotNull PsiStatement body,
                     @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    int appendOffset = 1;
    PsiMethodCallExpression appendCall = StringBufferJoinHandling.getMethodCall(tb.getStatements()[tb.getStatements().length - appendOffset]);
    if (appendCall == null) {
      if (tb.getStatements().length != 3) return null;
      appendCall = StringBufferJoinHandling.getMethodCall(tb.getStatements()[tb.getStatements().length - ++appendOffset]);
      if (appendCall == null) return null;
    }
    PsiVariable var = StringBufferJoinHandling.getCallVariable(appendCall);
    if (var == null || !var.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER)) return null;

    PsiExpression appended = StringBufferJoinHandling.getAppendParam(var, tb.getStatements()[tb.getStatements().length - appendOffset]);
    if (appended == null) return null;

    PsiType type = TypeUtils.getType(CommonClassNames.JAVA_LANG_STRING, body);

    PsiVariable checkVar = null;

    StringBuilder builder = generateStream(new MapOp(tb.getLastOperation(), appended, tb.getVariable(), type));
    builder.append(".collect(java.util.stream.Collectors.joining(");
    if (delimiter) {
      if (!(tb.getStatements()[0] instanceof PsiIfStatement)) return null;

      PsiIfStatement ifStmt = (PsiIfStatement) tb.getStatements()[0];
      checkVar = StringBufferJoinHandling.getFIVariable(ifStmt);

      boolean found = false;
      for (PsiStatement branch : new PsiStatement[] { ifStmt.getThenBranch(), ifStmt.getElseBranch() }) {
        if (branch == null) continue;
        PsiCodeBlock branchBody = ((PsiBlockStatement) branch).getCodeBlock();
        if (branchBody.getStatements().length != 1) return null;

        PsiExpression delim = StringBufferJoinHandling.getAppendParam(var, branchBody.getStatements()[0]);
        if (delim == null || delim.getType() == null || !delim.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) continue;

        builder.append(delim.getText());
        found = true;
      }
      if (!found) return null; // Failed to get the delimiter string
    }
    builder.append("))");

    return replaceWithStringConcatenation(project, loopStatement, var, checkVar, builder, type);
  }

}
