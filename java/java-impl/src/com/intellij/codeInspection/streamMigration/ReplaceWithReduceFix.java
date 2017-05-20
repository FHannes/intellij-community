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
                                            @NotNull StreamApiMigrationInspection.TerminalBlock tb,
                                            @NotNull ReduceHandling.ReductionData data) {
    StringBuilder result = new StringBuilder(".reduce(");

    // Get identity value
    result.append(data.getOperatorData().getFirst());

    result.append(", (a, b) -> ");
    result.append(String.format(data.getFormat(), data.isReversed() ? "b" : "a", data.isReversed() ? "a" : "b"));
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
    builder.append(replaceWithReduction(loopStatement, accumulator, stmt, tb, data));
    if (status == StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN || init == null) {
      String newExpression = accumulator.getName() + " = " + String.format(data.getFormat(), accumulator.getName(), builder.toString()) + ";";
      return loopStatement.replace(elementFactory.createStatementFromText(newExpression, loopStatement));
    } else {
      String newExpression = builder.toString();
      if (!init.getText().equals(data.getOperatorData().getFirst())) {
        String initText = init.getText();
        if (init instanceof PsiBinaryExpression) {
          initText = '(' + initText + ')';
        }
        newExpression = String.format(data.getFormat(), initText, newExpression);
      }
      return replaceInitializer(loopStatement, accumulator, init, newExpression, status);
    }
  }

}
