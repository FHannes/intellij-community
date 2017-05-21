package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Stream;

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
  static StringBuilder generateStream(@NotNull StreamApiMigrationInspection.Operation lastOperation, @NotNull String element) {
    StringBuilder buffer = new StringBuilder();
    List<String> replacements =
      StreamEx.iterate(lastOperation, Objects::nonNull, StreamApiMigrationInspection.Operation::getPreviousOp).map(
        StreamApiMigrationInspection.Operation::createReplacement).toList();
    boolean first = true;
    for (ListIterator<String> it = replacements.listIterator(replacements.size()); it.hasPrevious(); ) {
      if (first) {
        buffer.append(CommonClassNames.JAVA_UTIL_STREAM_STREAM);
        buffer.append(".concat(");
        buffer.append(CommonClassNames.JAVA_UTIL_STREAM_STREAM);
        buffer.append(".of(").append(element).append("), ");
      }
      buffer.append(it.previous());
      if (first) {
        buffer.append(")");
        first = false;
      }
    }
    return buffer;
  }

  @NotNull
  static StringBuilder appendReduce(@NotNull StringBuilder builder, @NotNull ReduceHandling.ReductionData data) {
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

    PsiExpression init = accumulator.getInitializer();
    StreamApiMigrationInspection.InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(accumulator, loopStatement);

    if (status == StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN || init == null) {
      String newExpression = accumulator.getName() + " = ";
      if (data.getOperatorData().getFirst().isEmpty()) {
        String streamExpr = appendReduce(generateStream(op, accumulator.getName()), data).toString();
        streamExpr += ".orElse(" + accumulator.getName() + ");";
        newExpression += streamExpr;
      } else {
        String streamExpr = appendReduce(generateStream(op), data).toString();
        newExpression += String.format(data.getFormat(), accumulator.getName(), streamExpr) + ";";
      }
      return loopStatement.replace(elementFactory.createStatementFromText(newExpression, loopStatement));
    } else {
      String newExpression = "";
      if (data.getOperatorData().getFirst().isEmpty()) {
        String streamExpr = appendReduce(generateStream(op, init.getText()), data).toString();
        if (data.getOperatorData().getFirst().isEmpty()) {
          streamExpr += ".orElse(" + init.getText() + ")";
        }
        newExpression += streamExpr;
      } else {
        if (!init.getText().equals(data.getOperatorData().getFirst())) {
          String initText = init.getText();
          if (init instanceof PsiBinaryExpression &&
              // Check for static method call, no need for braces if method param
              !data.getOperatorData().getFirst().contains(",")) {
            initText = '(' + initText + ')';
          }
          newExpression = String.format(data.getFormat(), initText, appendReduce(generateStream(op), data).toString());
        } else {
          newExpression = appendReduce(generateStream(op), data).toString();
        }
      }
      return replaceInitializer(loopStatement, accumulator, init, newExpression, status);
    }
  }

}
