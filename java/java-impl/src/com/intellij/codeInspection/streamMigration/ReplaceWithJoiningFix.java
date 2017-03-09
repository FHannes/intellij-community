package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
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
    if (!delimiter) {
      PsiMethodCallExpression appendCall = tb.getSingleExpression(PsiMethodCallExpression.class);

      PsiVariable var = StringBufferJoinHandling.extractStringBuilder(appendCall);
      if (var == null) return null;

      PsiExpression appended = StringBufferJoinHandling.getSingleExprParam(appendCall);
      if (appended == null) return null;

      PsiType type = tb.getVariable().getType();

      StringBuilder builder = generateStream(new MapOp(tb.getLastOperation(), appended, tb.getVariable(), type));
      builder.append(".collect(java.util.stream.Collectors.joining(");
      builder.append("))");

      return replaceWithStringConcatenation(project, loopStatement, var, builder, type);
    }

    return null;
  }

}
