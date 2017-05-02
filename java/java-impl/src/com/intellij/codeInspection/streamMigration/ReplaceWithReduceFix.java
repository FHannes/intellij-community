package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Frédéric Hannes
 */
class ReplaceWithReduceFix extends MigrateToStreamFix {

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with reduce()";
  }

  @Nullable
  public static String replaceWithReduction(@NotNull PsiLoopStatement loopStatement,
                                            @NotNull PsiVariable var,
                                            @NotNull PsiAssignmentExpression assignment,
                                            @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    if (!(assignment.getLExpression() instanceof PsiReferenceExpression)) return null;
    PsiVariable accumulator = ReduceHandling.resolveVariable(assignment.getLExpression());
    if (accumulator == null) return null;

    StringBuilder result = new StringBuilder(".reduce(");

    PsiExpression init = var.getInitializer();
    StreamApiMigrationInspection.InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(var, loopStatement);
    if (status == StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN || init == null) {
      result.append(accumulator.getName());
    } else {
      result.append(init.getText());
    }

    result.append(", (a, b) -> ");

    if (JavaTokenType.PLUSEQ.equals(assignment.getOperationTokenType())) {
      result.append("a + ");
      if (ExpressionUtils.isReferenceTo(assignment.getRExpression(), tb.getVariable())) {
        result.append('b');
      } else {
        result.append(assignment.getRExpression().getText());
      }
    } else if (JavaTokenType.ASTERISKEQ.equals(assignment.getOperationTokenType())) {
      result.append("a * ");
      if (ExpressionUtils.isReferenceTo(assignment.getRExpression(), tb.getVariable())) {
        result.append('b');
      } else {
        result.append(assignment.getRExpression().getText());
      }
    } else if (JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      if (assignment.getRExpression() instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)assignment.getRExpression();
        String operator = JavaTokenType.PLUS.equals(binOp.getOperationTokenType()) ? "+" : "*";
        boolean plainOp = ExpressionUtils.isReferenceTo(binOp.getLOperand(), accumulator);
        String leftOp = plainOp ? "a" : "b", rightOp = plainOp ? "b" : "a";
        if (ExpressionUtils.isReferenceTo(binOp.getLOperand(), accumulator) &&
            !ExpressionUtils.isReferenceTo(binOp.getROperand(), tb.getVariable())) {
          rightOp = binOp.getROperand().getText();
        } else if (ExpressionUtils.isReferenceTo(binOp.getROperand(), accumulator) &&
                   !ExpressionUtils.isReferenceTo(binOp.getLOperand(), tb.getVariable())) {
          leftOp = binOp.getLOperand().getText();
        }
        result.append(leftOp).append(' ').append(operator).append(' ').append(rightOp);
      } else if (assignment.getRExpression() instanceof PsiMethodCallExpression) {
        // Check that accumulator is valid as a method call on the accumulator variable
        PsiMethodCallExpression mce = (PsiMethodCallExpression) assignment.getRExpression();
        if (mce == null) return null;

        // Resolve to the method declaration to verify the annotation for associativity
        PsiElement element = mce.getMethodExpression().resolve();
        if (element == null || !(element instanceof PsiMethod)) return null;
        PsiMethod operation = (PsiMethod) element;

        boolean plainOp = ExpressionUtils.isReferenceTo(mce.getMethodExpression().getQualifierExpression(), accumulator);
        String leftOp = plainOp ? "a" : "b", rightOp = plainOp ? "b" : "a";

        if (!ExpressionUtils.isReferenceTo(mce.getArgumentList().getExpressions()[0], tb.getVariable())) {
          rightOp = mce.getArgumentList().getExpressions()[0].getText();
        }

        result.append(leftOp).append('.').append(operation.getName()).append('(').append(rightOp).append(')');
      }
    }
    result.append(")");
    return result.toString();
  }

  @Override
  PsiElement migrate(@NotNull Project project,
                     @NotNull PsiLoopStatement loopStatement,
                     @NotNull PsiStatement body,
                     @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    PsiAssignmentExpression stmt = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (stmt == null) return null;

    PsiVariable accumulator = ReduceHandling.getReductionAccumulator(stmt);
    if (accumulator == null) return null;

    restoreComments(loopStatement, loopStatement.getBody());

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    StreamApiMigrationInspection.Operation op = tb.getLastOperation();
    StringBuilder builder = generateStream(op);

    PsiExpression init = accumulator.getInitializer();
    StreamApiMigrationInspection.InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(accumulator, loopStatement);
    builder.append(replaceWithReduction(loopStatement, accumulator, stmt, tb));
    if (status == StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN || init == null) {
      builder.insert(0, " = ");
      builder.insert(0, accumulator.getName());
      builder.append(";");
      return loopStatement.replace(elementFactory.createStatementFromText(builder.toString(), loopStatement));
    } else {
      return replaceInitializer(loopStatement, accumulator, init, builder.toString(), status);
    }
  }

}
