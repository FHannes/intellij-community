package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Frédéric Hannes
 */
class ReplaceWithJoiningFix extends MigrateToStreamFix {

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with joining()";
  }

  @Override
  PsiElement migrate(@NotNull Project project,
                     @NotNull PsiLoopStatement loopStatement,
                     @NotNull PsiStatement body,
                     @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    /*PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    String delimeter = null;
    if (assignment == null) {
      // complex joining operation
      PsiStatement[] stmts = tb.getStatements();
      if (stmts.length != 2) return null; // TODO: Check if there's an else branch which holds part of the joining operation
      if (stmts[0] instanceof PsiIfStatement && stmts[1] instanceof PsiExpressionStatement) {
        if (!(((PsiExpressionStatement) stmts[1]).getExpression() instanceof PsiAssignmentExpression)) return null;
        PsiCodeBlock ifthen = StreamApiMigrationInspection.getExcludeFirstIterationBlock(tb);
        if (ifthen == null || ifthen.getStatements().length != 1) return null;
        if (!(ifthen.getStatements()[0] instanceof PsiExpressionStatement)) return null;
        if (!(((PsiExpressionStatement) ifthen.getStatements()[0]).getExpression() instanceof PsiAssignmentExpression)) return null;
        PsiAssignmentExpression adddelim = (PsiAssignmentExpression) ((PsiExpressionStatement) ifthen.getStatements()[0]).getExpression();
        assignment = (PsiAssignmentExpression) ((PsiExpressionStatement) stmts[1]).getExpression();
        if (StreamApiMigrationInspection.extractAccumulator(assignment) !=
            StreamApiMigrationInspection.extractAccumulator(adddelim)) return null;
        PsiExpression delim = StreamApiMigrationInspection.extractAddend(adddelim);
        if (!(delim instanceof PsiLiteralExpression && ((PsiLiteralExpression) delim).getValue() instanceof String)) return null;
        delimeter = (String) ((PsiLiteralExpression) delim).getValue();
      }
    }

    PsiVariable var = StreamApiMigrationInspection.extractAccumulator(assignment);
    if (var == null) return null;

    PsiType type = var.getType();
    if (!type.equalsToText(String.class.getName())) return null;

    PsiExpression addend = StreamApiMigrationInspection.extractAddend(assignment);
    if (addend == null) return null;

    PsiType addendType = addend.getType();
    if (addendType != null && !TypeConversionUtil.isAssignable(type, addendType)) {
      addend = JavaPsiFacade.getElementFactory(project).createExpressionFromText(
        "(" + type.getCanonicalText() + ")" + ParenthesesUtils.getText(addend, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE), addend);
    }

    StringBuilder builder = generateStream(new MapOp(tb.getLastOperation(), addend, tb.getVariable(), type));
    builder.append(".collect(java.util.stream.Collectors.joining(");
    if (delimeter != null) {
      builder.append('"').append(delimeter).append('"');
    }
    builder.append("))");

    return replaceWithStringConcatenation(project, loopStatement, var, builder, type);*/
    return null;
  }
}
