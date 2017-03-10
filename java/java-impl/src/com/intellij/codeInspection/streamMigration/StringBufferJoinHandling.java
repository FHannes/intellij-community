package com.intellij.codeInspection.streamMigration;

import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * @author Frédéric Hannes
 */
public class StringBufferJoinHandling {

  public static PsiVariable getCallVariable(PsiMethodCallExpression call) {
    // Resolve to object call is made on
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression) qualifierExpression).resolve();
    if (!(resolved instanceof PsiVariable)) return null;
    return (PsiVariable) resolved;
  }

  /**
   * Checks whether a given (local) variable is initialized with a constructor containing a given maximum amount of elements.
   *
   * @param var
   * @param max
   * @return
   */
  private static boolean checkInitArguments(PsiVariable var, String[][] args) {
    // Variable must be local
    if (!(var instanceof PsiLocalVariable)) return false;

    // Check if initial StringBuilder is empty
    PsiExpression initializer = var.getInitializer();
    if (!(initializer instanceof PsiNewExpression)) return false;

    // Check for a valid class reference for the constructor
    PsiNewExpression initNew = (PsiNewExpression) initializer;
    if (initNew.getClassReference() == null) return false;
    PsiElement constrClass = initNew.getClassReference().resolve();
    if (!(constrClass instanceof PsiClass) ||
        !var.getType().getCanonicalText().equals(((PsiClass) constrClass).getQualifiedName())) return false;

    // Check the arguments for the constructor
    if (initNew.getArgumentList() == null) return false;

    for (String[] argList : args) {
      if (argList == null) continue;
      if (initNew.getArgumentList().getExpressions().length != argList.length) continue;
      boolean success = true;
      for (int idx = 0; idx < argList.length; idx++) {
        if (initNew.getArgumentList().getExpressions()[0].getType() == null) {
          success = false;
          break;
        }
        if (!initNew.getArgumentList().getExpressions()[0].getType().equalsToText(argList[0])) {
          success = false;
          break;
        }
      }
      if (success) {
        return true;
      }
    }

    return false;
  }

  /**
   * Gets a specific argument from the constructor used to initialize a local variable.
   *
   * @param var
   * @param arg
   * @return
   */
  public static PsiExpression getInitArgument(PsiVariable var, int arg) {
    // Variable must be local
    if (!(var instanceof PsiLocalVariable)) return null;

    // Check if initial StringBuilder is empty
    PsiExpression initializer = var.getInitializer();
    if (!(initializer instanceof PsiNewExpression)) return null;

    // Check for a valid class reference for the constructor
    PsiNewExpression initNew = (PsiNewExpression) initializer;
    if (initNew.getClassReference() == null) return null;
    PsiElement constrClass = initNew.getClassReference().resolve();
    if (!(constrClass instanceof PsiClass) ||
        !var.getType().getCanonicalText().equals(((PsiClass) constrClass).getQualifiedName())) return null;

    if (initNew.getArgumentList() == null || initNew.getArgumentList().getExpressions().length <= arg) return null;

    // Get the requested argument for the constructor
    return initNew.getArgumentList().getExpressions()[arg];
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
  private static PsiAssignmentExpression getAssignment(PsiStatement stmt) {
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

  @Nullable
  public static PsiExpression getAppendParam(PsiVariable sb, PsiStatement stmt) {
    // Input must be valid
    if (sb == null || stmt == null) return null;
    // The input variable must be a StringBuilder
    if (!sb.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER)) return null;
    // The input statement must be a method call
    PsiMethodCallExpression call = getMethodCall(stmt);
    if (call == null) return null;
    // The method call must be an append operation with a single argument
    if (!"append".equals(call.getMethodExpression().getReferenceName())) return null;
    if (call.getArgumentList().getExpressions().length != 1) return null;
    // The variable the method call is performed on must be the given StringBuilder
    if (!sb.equals(getCallVariable(call))) return null;
    return call.getArgumentList().getExpressions()[0];
  }

  @Nullable
  public static PsiVariable getJoinedVariable(StreamApiMigrationInspection.TerminalBlock tb, List<PsiVariable> variables) {
    // Only works for loops with (at least one and) at most two statements [if-statement with body counts as one]
    if (tb.getStatements().length != 0 && tb.getStatements().length > 2) return null;

    // We only concatenate strings
    PsiVariable itVar = tb.getVariable();
    if (!itVar.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) return null;

    // String concatenation if one variable is a StringBuffer
    Optional<PsiVariable> sbVar = StreamEx.of(variables.stream())
      .findFirst(v -> v.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER));
    if (!sbVar.isPresent()) return null;

    // String concatenation with delim needs one boolean switch var
    Optional<PsiVariable> switchVar = StreamEx.of(variables.stream())
      .findFirst(v -> v.getType().isAssignableFrom(PsiType.BOOLEAN));

    if (switchVar.isPresent() && tb.getStatements().length == 2) {
      // Check if the switch is used after it's definition
      if (tb.isReferencedInOperations(switchVar.get())) return null;

      boolean initVal = false;

      // In case the boolean switch is initialized, get the init value
      PsiExpression initializer = switchVar.get().getInitializer();
      if (initializer instanceof PsiLiteralExpression) {
        if (PsiType.BOOLEAN.equals(initializer.getType())) {
          Boolean tVal = (Boolean)((PsiLiteralExpression)initializer).getValue();
          if (tVal == null) return null; // Value can not be set to null
          initVal = tVal;
        }
      }

      // The second statement in the loop must be an if-statement
      if (!(tb.getStatements()[0] instanceof PsiIfStatement)) return null;

      PsiIfStatement ifStmt = (PsiIfStatement) tb.getStatements()[0];

      // Check if-condition
      if (initVal) {
        // Check positive switch (check value is true)
        if (!ExpressionUtils.isReferenceTo(ifStmt.getCondition(), switchVar.get())) return null;
      } else {
        // Check negative switch (check value is false)
        if (!isNegatedReferenceTo(ifStmt.getCondition(), switchVar.get())) return null;
      }

      // Check correct switching on first iteration
      if (ifStmt.getThenBranch() == null) return null;
      PsiCodeBlock thenBody = ((PsiBlockStatement) ifStmt.getThenBranch()).getCodeBlock();
      if (thenBody.getStatements().length != 1) return null;
      PsiAssignmentExpression assignStmt = getAssignment(thenBody.getStatements()[0]);
      if (assignStmt == null) return null;
      if (!JavaTokenType.EQ.equals(assignStmt.getOperationTokenType())) return null;
      if (!ExpressionUtils.isReferenceTo(assignStmt.getLExpression(), switchVar.get())) return null;
      if (!isNegatedReferenceTo(assignStmt.getRExpression(), switchVar.get())) {
        if (!(assignStmt.getRExpression() instanceof PsiLiteralExpression)) return null;
        if (!PsiType.BOOLEAN.equals(assignStmt.getRExpression().getType())) return null;
        Boolean tVal = (Boolean)((PsiLiteralExpression)assignStmt.getRExpression()).getValue();
        if (tVal == null || tVal.equals(initVal)) return null;
      }

      // Check if delimiter is correct
      if (ifStmt.getElseBranch() == null) return null;
      PsiCodeBlock elseBody = ((PsiBlockStatement) ifStmt.getElseBranch()).getCodeBlock();
      if (elseBody.getStatements().length != 1) return null;
      PsiExpression delim = getAppendParam(sbVar.get(), elseBody.getStatements()[0]);
      if (delim == null || delim.getType() == null || !delim.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) return null;
    }

    // The StringBuilder should be constructed with at most one argument
    if (!checkInitArguments(sbVar.get(), new String[][] {
      ArrayUtil.EMPTY_STRING_ARRAY, new String[] { CommonClassNames.JAVA_LANG_STRING }
    })) return null;

    // Check if the StringBuilder is used after creation
    if (tb.isReferencedInOperations(sbVar.get())) return null;

    // The TerminalBlock must contain a single append operation
    PsiExpression appendParam = getAppendParam(sbVar.get(), tb.getStatements()[tb.getStatements().length - 1]);
    if (!ExpressionUtils.isReferenceTo(appendParam, tb.getVariable())) return null;

    return sbVar.get();
  }

}
