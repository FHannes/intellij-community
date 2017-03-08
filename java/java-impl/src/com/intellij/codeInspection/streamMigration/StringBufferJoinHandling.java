package com.intellij.codeInspection.streamMigration;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Frédéric Hannes
 */
public class StringBufferJoinHandling {

  @Nullable
  static PsiVariable getExclusionVar(StreamApiMigrationInspection.TerminalBlock tb) {
    // The TerminalBlock should contain an if-statement
    PsiStatement[] stmts = tb.getStatements();
    if (stmts.length == 0 || !(stmts[0] instanceof PsiIfStatement)) return null;

    // The if-statement should be a binary expression (having a left and right operand, combined with an operation)
    PsiIfStatement ifstmt = (PsiIfStatement) stmts[0];
    if (!(ifstmt.getCondition() instanceof PsiBinaryExpression)) return null;

    // The binary expression should have an operand with a literal value and a variable, as well as perform a not-equals check
    PsiBinaryExpression bestmt = (PsiBinaryExpression) ifstmt.getCondition();
    if (!bestmt.getOperationTokenType().equals(JavaTokenType.NE)) return null;
    PsiLiteralExpression checkVal =
      bestmt.getROperand() instanceof PsiLiteralExpression ? (PsiLiteralExpression) bestmt.getROperand() :
      bestmt.getLOperand() instanceof PsiLiteralExpression ? (PsiLiteralExpression) bestmt.getLOperand() : null;
    PsiReferenceExpression checkVar =
      bestmt.getROperand() instanceof PsiReferenceExpression ? (PsiReferenceExpression) bestmt.getROperand() :
      bestmt.getLOperand() instanceof PsiReferenceExpression ? (PsiReferenceExpression) bestmt.getLOperand() : null;

    // The variable operand should be a local variable
    if (checkVal == null || checkVar == null) return null;
    PsiElement cvRes = checkVar.resolve();
    if (!(cvRes instanceof PsiLocalVariable)) return null;
    PsiLocalVariable checkVarLocal = (PsiLocalVariable) cvRes;

    // The variable should be initialized to the value found as an operand for the binary expression
    if (!(checkVarLocal.getInitializer() instanceof PsiLiteralExpression)) return null;
    Object initValue = ((PsiLiteralExpression) checkVarLocal.getInitializer()).getValue();
    Object checkValue = checkVal.getValue();
    if (initValue == null || checkValue == null || !initValue.equals(checkValue)) return null;

    // The if-statement must have a body
    if (ifstmt.getThenBranch() == null) return null;

    /*
     * The if-statement's body should have only 2 statements, as we expect it to contain an assignment to the operand variable to chance its
     * value, as well as the appending of the delimiter to the StringBuilder.
     */
    PsiCodeBlock ifBody = ((PsiBlockStatement) ifstmt.getThenBranch()).getCodeBlock();
    if (ifBody.getStatements().length != 2) return null;

    //PsiStatement assignStmt = ifBody.getStatements()[0].;


    // TODO: Check if variable is changed after the first iteration
    return checkVarLocal;
  }

  // Based on isAddAllCall() method
  private static boolean isAppendCall(StreamApiMigrationInspection.TerminalBlock tb, PsiMethodCallExpression call) {
    if (!ExpressionUtils.isReferenceTo(call.getArgumentList().getExpressions()[0], tb.getVariable())) return false;
    if (!"append".equals(call.getMethodExpression().getReferenceName())) return false;
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (qualifierExpression == null || qualifierExpression instanceof PsiThisExpression) {
      PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
      return method == null || !method.getName().equals("append");
    }
    return !(qualifierExpression instanceof PsiMethodCallExpression);
  }

  private static boolean isCallSubject(PsiVariable var, PsiMethodCallExpression call) {
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression)) return false;
    PsiElement resolved = ((PsiReferenceExpression) qualifierExpression).resolve();
    return resolved != null && resolved.equals(var);
  }

  @Nullable
  public static PsiVariable getJoinedVariable(StreamApiMigrationInspection.TerminalBlock tb, List<PsiVariable> variables) {
    // We only concatenate strings
    PsiVariable itVar = tb.getVariable();
    if (!itVar.getType().equalsToText("java.lang.String")) return null;

    // TODO: Check if StringBuilder is created empty?
    switch (variables.size()) {
      case 1:
        // String concatenation if one variable is a StringBuffer
        PsiVariable var = variables.get(0);
        if (!var.getType().equalsToText("java.lang.StringBuilder")) return null;

        // Check if the StringBuilder is used after creation
        if (tb.isReferencedInOperations(var)) return null;

        // The terminalBlock must contain a single append operation
        PsiMethodCallExpression appendCall = tb.getSingleExpression(PsiMethodCallExpression.class);
        if (appendCall == null || !isAppendCall(tb, appendCall)) return null;
        // The append operation must be called on the StringBuilder
        if (!isCallSubject(var, appendCall)) return null;

        return var;
      case 2:
      default:
        return null;
    }

    /*PsiMethodCallExpression append = tb.getSingleExpression(PsiMethodCallExpression.class);
    if (append != null) {
      // complex joining operation
      PsiStatement[] stmts = tb.getStatements();
      if (stmts.length != 2) return null; // TODO: Check if there's an else branch which holds part of the joining operation
      if (stmts[0] instanceof PsiIfStatement && stmts[1] instanceof PsiExpressionStatement) {
        if (!(((PsiExpressionStatement) stmts[1]).getExpression() instanceof PsiAssignmentExpression)) return null;
        PsiCodeBlock ifthen = getExcludeFirstIterationBlock(tb);
        if (ifthen == null || ifthen.getStatements().length != 1) return null;
        if (!(ifthen.getStatements()[0] instanceof PsiExpressionStatement)) return null;
        if (!(((PsiExpressionStatement) ifthen.getStatements()[0]).getExpression() instanceof PsiAssignmentExpression)) return null;
        PsiAssignmentExpression adddelim = (PsiAssignmentExpression) ((PsiExpressionStatement) ifthen.getStatements()[0]).getExpression();
        append = (PsiAssignmentExpression) ((PsiExpressionStatement) stmts[1]).getExpression();
        if (extractAccumulator(append) != extractAccumulator(adddelim)) return null;
        PsiExpression delim = extractAddend(adddelim);
        if (!(delim instanceof PsiLiteralExpression && ((PsiLiteralExpression) delim).getValue() instanceof String)) return null;
      }
    }
    PsiVariable var = extractAccumulator(append);

    // the referred variable is the same as non-final variable
    if (var == null || !variables.contains(var)) return null;
    if (!var.getType().equalsToText(String.class.getName())) return null;

    // the referred variable is not used in intermediate operations
    if(isReferencedInOperations(var, tb)) return null;
    PsiExpression addend = extractAddend(append);
    LOG.assertTrue(addend != null);
    if(ReferencesSearch.search(var, new LocalSearchScope(addend)).findFirst() != null) return null;
    return variables.get(0);*/
  }

}
