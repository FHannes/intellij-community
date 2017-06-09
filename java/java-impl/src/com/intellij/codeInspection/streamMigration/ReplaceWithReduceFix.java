package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Frédéric Hannes
 */
class ReplaceWithReduceFix extends MigrateToStreamFix {

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with reduce()";
  }

  @NotNull
  private static StringBuilder appendReduce(StringBuilder builder, ReduceHandling.ReductionData data) {
    builder.append(".reduce(");
    if (!data.getOperatorData().getFirst().isEmpty()) {
      builder.append(data.getOperatorData().getFirst());
      builder.append(", ");
    }
    builder.append("(a, b) -> ");
    builder.append(String.format(data.getFormat(), data.isReversed() ? "b" : "a", data.isReversed() ? "a" : "b"));
    builder.append(")");
    return builder;
  }

  @NotNull
  private static StringBuilder prependStreamElement(StringBuilder builder, String element) {
    builder.append(")");
    builder.insert(0, "), ");
    builder.insert(0, element);
    builder.insert(0, ".of(");
    builder.insert(0, CommonClassNames.JAVA_UTIL_STREAM_STREAM);
    builder.insert(0, ".concat(");
    builder.insert(0, CommonClassNames.JAVA_UTIL_STREAM_STREAM);
    return builder;
  }

  @Override
  PsiElement migrate(@NotNull Project project,
                     @NotNull PsiLoopStatement loopStatement,
                     @NotNull PsiStatement body,
                     @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    tb = tb.tryPeelLimit(loopStatement);

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

    PsiExpression init = accumulator.getInitializer();
    ControlFlowUtils.InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(accumulator, loopStatement);

    if (status == ControlFlowUtils.InitializerUsageStatus.UNKNOWN || init == null) {
      StringBuilder builder = generateStream(op);
      if (data.getOperatorData().getFirst().isEmpty() && !data.getOperatorData().getSecond()) {
        builder = prependStreamElement(builder, accumulator.getName());
      }
      builder = appendReduce(builder, data);
      String newExpression = accumulator.getName() + " = ";
      String streamExpr = builder.toString();
      if (data.getOperatorData().getFirst().isEmpty()) {
        streamExpr += ".orElse(" + accumulator.getName() + ")";
      }

      if (data.getOperatorData().getFirst().isEmpty() && !data.getOperatorData().getSecond()) {
        newExpression += streamExpr;
      } else {
        newExpression += String.format(data.getFormat(), accumulator.getName(), streamExpr) + ";";
      }
      return loopStatement.replace(elementFactory.createStatementFromText(newExpression, loopStatement));
    } else {
      StringBuilder builder = generateStream(op);
      String newExpression = "";
      if (data.getOperatorData().getFirst().isEmpty()) {
        if (!data.getOperatorData().getSecond()) {
          builder = prependStreamElement(builder, init.getText());
        }
        builder = appendReduce(builder, data);
        String streamExpr = builder.toString();
        if (data.getOperatorData().getFirst().isEmpty()) {
          streamExpr += ".orElse(" + init.getText() + ")";
        }
        if (data.getOperatorData().getSecond()) {
          newExpression += String.format(data.getFormat(), init.getText(), streamExpr);
        } else {
          newExpression += streamExpr;
        }
      } else {
        builder = appendReduce(builder, data);
        if (!ReduceHandling.isSameExpression(init, elementFactory.createExpressionFromText(data.getOperatorData().getFirst(), loopStatement))) {
          String initText = init.getText();
          if (init instanceof PsiBinaryExpression &&
              // Check for static method call, no need for braces if method param
              !data.getFormat().contains(",")) {
            initText = '(' + initText + ')';
          }
          newExpression = String.format(data.getFormat(), initText, builder.toString());
        } else {
          newExpression = builder.toString();
        }
      }

      return replaceInitializer(loopStatement, accumulator, init, newExpression, status);
    }
  }

}
