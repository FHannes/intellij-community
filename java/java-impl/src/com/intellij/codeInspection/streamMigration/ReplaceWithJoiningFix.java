package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
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
    PsiMethodCallExpression appendCall = StringBufferJoinHandling.getMethodCall(tb.getStatements()[tb.getStatements().length - 1]);
    if (appendCall == null) return null;
    PsiVariable var = StringBufferJoinHandling.getCallVariable(appendCall);
    if (var == null || !var.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER)) return null;

    PsiExpression appended = StringBufferJoinHandling.getAppendParam(var, tb.getStatements()[tb.getStatements().length - 1]);
    if (appended == null) return null;

    PsiType type = tb.getVariable().getType();

    StringBuilder builder = generateStream(new MapOp(tb.getLastOperation(), appended, tb.getVariable(), type));
    builder.append(".collect(java.util.stream.Collectors.joining(");
    if (delimiter) {
      if (!(tb.getStatements()[0] instanceof PsiIfStatement)) return null;

      PsiIfStatement ifStmt = (PsiIfStatement) tb.getStatements()[0];
      if (ifStmt.getElseBranch() == null) return null;
      PsiCodeBlock elseBody = ((PsiBlockStatement) ifStmt.getElseBranch()).getCodeBlock();
      if (elseBody.getStatements().length != 1) return null;

      PsiExpression delim = StringBufferJoinHandling.getAppendParam(var, elseBody.getStatements()[0]);
      if (delim == null || delim.getType() == null || !delim.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) return null;

      builder.append(delim.getText());
      // TODO: Remove switch boolean?
    }
    builder.append("))");

    return replaceWithStringConcatenation(project, loopStatement, var, builder, type);
  }

}
