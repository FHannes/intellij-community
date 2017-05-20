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

  @Override
  PsiElement migrate(@NotNull Project project,
                     @NotNull PsiLoopStatement loopStatement,
                     @NotNull PsiStatement body,
                     @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    PsiAssignmentExpression stmt = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (stmt == null) return null;

    ReduceHandling.ReductionData data = ReduceHandling.getReductionAccumulator(stmt);
    if (data == null) return null;
    PsiVariable accumulator = data.getAccumulator();
    if (accumulator == null) return null;

    restoreComments(loopStatement, loopStatement.getBody());

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    StreamApiMigrationInspection.Operation op = tb.getLastOperation();
    if (!data.getExpression().equals(tb.getVariable())) {
      op = new StreamApiMigrationInspection.MapOp(op, data.getExpression(), tb.getVariable(), accumulator.getType());
    }
    StringBuilder builder = generateStream(op);

    PsiExpression init = accumulator.getInitializer();
    StreamApiMigrationInspection.InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(accumulator, loopStatement);

    builder.append(".reduce(");
    if (!data.getOperatorData().getFirst().isEmpty()) {
      builder.append(data.getOperatorData().getFirst());
      builder.append(", ");
    }
    builder.append("(a, b) -> ");
    builder.append(String.format(data.getFormat(), data.isReversed() ? "b" : "a", data.isReversed() ? "a" : "b"));
    builder.append(")");

    if (status == StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN || init == null) {
      String newExpression = accumulator.getName() + " = ";
      String streamExpr = builder.toString();
      if (data.getOperatorData().getFirst().isEmpty()) {
        streamExpr += ".orElse(" + accumulator.getName() + ");";
      }
      newExpression += String.format(data.getFormat(), accumulator.getName(), streamExpr) + ";";
      return loopStatement.replace(elementFactory.createStatementFromText(newExpression, loopStatement));
    } else {
      String newExpression = "";
      if (data.getOperatorData().getFirst().isEmpty()) {
        String streamExpr = builder.toString();
        if (data.getOperatorData().getFirst().isEmpty()) {
          streamExpr += ".orElse(" + init.getText() + ")";
        }
        newExpression += String.format(data.getFormat(), init.getText(), streamExpr);
      } else {
        if (!init.getText().equals(data.getOperatorData().getFirst())) {
          String initText = init.getText();
          if (init instanceof PsiBinaryExpression &&
              // Check for static method call, no need for braces if method param
              !data.getOperatorData().getFirst().contains(",")) {
            initText = '(' + initText + ')';
          }
          newExpression = String.format(data.getFormat(), initText, builder.toString());
        }
      }
      return replaceInitializer(loopStatement, accumulator, init, newExpression, status);
    }
  }

}
