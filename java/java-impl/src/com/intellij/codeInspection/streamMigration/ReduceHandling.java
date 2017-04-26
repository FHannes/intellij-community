package com.intellij.codeInspection.streamMigration;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Frédéric Hannes
 */
public class ReduceHandling {

  public static final String BE_KULEUVEN_CW_DTAI_ASSOCIATIVE = "be.kuleuven.cw.dtai.Associative";

  @Nullable
  public static PsiVariable resolveVariable(PsiExpression expr) {
    if (!(expr instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
    if (!(resolved instanceof PsiVariable)) return null;
    return (PsiVariable) resolved;
  }

  @Nullable
  public static PsiVariable getReductionAccumulator(PsiAssignmentExpression assignment) {
    if (!(assignment.getLExpression() instanceof PsiReferenceExpression)) return null;
    PsiVariable accumulator = resolveVariable(assignment.getLExpression());
    if (accumulator == null) return null;

    if (JavaTokenType.PLUSEQ.equals(assignment.getOperationTokenType()) ||
        JavaTokenType.ASTERISKEQ.equals(assignment.getOperationTokenType())) {
      return accumulator; // Addition and multiplication are associative
    } else if (JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      if (assignment.getRExpression() instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)assignment.getRExpression();
        if (JavaTokenType.PLUS.equals(binOp.getOperationTokenType()) || JavaTokenType.ASTERISK.equals(binOp.getOperationTokenType())) {
          PsiExpression left = binOp.getLOperand();
          PsiExpression right = binOp.getROperand();
          if (ExpressionUtils.isReferenceTo(left, accumulator) || ExpressionUtils.isReferenceTo(right, accumulator)) {
            return accumulator; // Addition and multiplication are associative
          }
        }
      } else if (assignment.getRExpression() instanceof PsiMethodCallExpression) {
        // TODO: Check if call is made on accumulator variable
        // TODO: Check method call for associative operation (verify annotation)
      }
    }
    return null;
  }

  /**
   * Checks if a given expression is a negated reference to a given variable.
   *
   * @param expr
   * @param var
   * @return
   */
  private static boolean isNegatedReferenceTo(PsiExpression expr, PsiVariable var) {
    expr = ParenthesesUtils.stripParentheses(expr);
    if (!(expr instanceof PsiPrefixExpression)) return false;
    PsiPrefixExpression prefix = (PsiPrefixExpression) expr;
    if (!JavaTokenType.EXCL.equals(prefix.getOperationTokenType())) return false;
    if (!(prefix.getOperand() instanceof PsiReferenceExpression)) return false;
    return ExpressionUtils.isReferenceTo(prefix.getOperand(), var);
  }

  @Nullable
  public static PsiAssignmentExpression getAssignment(PsiStatement stmt) {
    if (!(stmt instanceof PsiExpressionStatement)) return null;
    PsiExpression expr = ((PsiExpressionStatement) stmt).getExpression();
    if (!(expr instanceof PsiAssignmentExpression)) return null;
    return (PsiAssignmentExpression) expr;
  }

  @Nullable
  public static PsiMethodCallExpression getMethodCall(PsiStatement stmt) {
    if (!(stmt instanceof PsiExpressionStatement)) return null;
    PsiExpression expr = ((PsiExpressionStatement) stmt).getExpression();
    if (!(expr instanceof PsiMethodCallExpression)) return null;
    return (PsiMethodCallExpression) expr;
  }

  public static boolean isFinal(PsiModifierListOwner element) {
    return element.getModifierList() != null && element.getModifierList().hasModifierProperty(PsiModifier.FINAL);
  }

  public static boolean isVariableRead(ControlFlow flow, int offset, PsiVariable variable) {
    Instruction instruction = flow.getInstructions().get(offset);
    return instruction instanceof ReadVariableInstruction && ((ReadVariableInstruction)instruction).variable == variable;
  }

  public static boolean isVariableWritten(ControlFlow flow, int offset, PsiVariable variable) {
    Instruction instruction = flow.getInstructions().get(offset);
    return instruction instanceof WriteVariableInstruction && ((WriteVariableInstruction)instruction).variable == variable;
  }

  @Nullable
  public static PsiVariable getReduceVar(StreamApiMigrationInspection.TerminalBlock tb, List<PsiVariable> variables) {
    PsiAssignmentExpression stmt = getAssignment(tb.getSingleStatement());
    if (stmt == null) return null;
    return getReductionAccumulator(stmt);
  }

}
