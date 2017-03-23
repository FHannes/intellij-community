package com.intellij.codeInspection.streamMigration;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN;

/**
 * @author Frédéric Hannes
 */
public class StringConcatHandling {

  public static PsiVariable resolveVariable(PsiExpression expr) {
    if (!(expr instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
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
      if (argList == null || initNew.getArgumentList().getExpressions().length != argList.length) continue;

      // All argument types must match
      if (IntStreamEx.range(0, argList.length)
        .mapToEntry(i -> initNew.getArgumentList().getExpressions()[i], i -> argList[i])
        .mapKeys(PsiExpression::getType)
        .nonNullKeys()
        .allMatch(e -> e.getKey().equalsToText(e.getValue()))) {
        return true;
      }
    }

    return false;
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

  @Nullable
  public static PsiExpression getAppendParam(PsiVariable concatVar, PsiStatement stmt, boolean stringConcat) {
    // Input must be valid
    if (concatVar == null || stmt == null) return null;
    if (stringConcat) {
      // The input variable must be a String
      if (!concatVar.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) return null;
      // The input statement must be an assignment call
      PsiAssignmentExpression assign = getAssignment(stmt);
      if (assign == null) return null;
      return StreamApiMigrationInspection.extractAddend(assign);
    } else {
      // The input variable must be a StringBuilder
      if (!concatVar.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER)) return null;
      // The input statement must be a method call
      PsiMethodCallExpression call = getMethodCall(stmt);
      if (call == null) return null;
      // The method call must be an append operation with a single argument
      if (!"append".equals(call.getMethodExpression().getReferenceName())) return null;
      if (call.getArgumentList().getExpressions().length != 1) return null;
      // The variable the method call is performed on must be the given StringBuilder
      if (!concatVar.equals(resolveVariable(call.getMethodExpression().getQualifierExpression()))) return null;
      return call.getArgumentList().getExpressions()[0];
    }
  }

  /**
   * Returns the variable which is used to check for the first iteration (FI) of a for-loop, in the given if-condition which originates at
   * the start of that for-loop.
   *
   * @param ifStmt
   * @return
   */
  @Nullable
  public static PsiVariable getFIVariable(PsiIfStatement ifStmt) {
    PsiExpression ifCond = ParenthesesUtils.stripParentheses(ifStmt.getCondition());
    if (ifCond instanceof PsiPrefixExpression) {
      ifCond = ((PsiPrefixExpression)ifCond).getOperand();
    }
    if (!(ifCond instanceof PsiReferenceExpression)) return null;
    PsiElement elem = ((PsiReferenceExpression)ifCond).resolve();
    // The variable must be local!
    if (!(elem instanceof PsiLocalVariable)) return null;
    return (PsiVariable) elem;
  }

  public static PsiDeclarationStatement getLocalDeclaration(PsiVariable var) {
    if (!(var instanceof PsiLocalVariable)) return null;
    if (!(var.getParent() instanceof PsiDeclarationStatement)) return null;
    return (PsiDeclarationStatement) var.getParent();
  }

  /**
   * Checks if the altering of the value of a variable which is used to check for the first iteration (FI) of a for-loop, is performed
   * correctly in a given statement.
   *
   * @param stmt
   * @param checkVar
   * @param initial
   * @param allowToggle
   * @return
   */
  public static boolean isValidFISetter(PsiStatement stmt, PsiVariable checkVar, boolean initial, boolean allowToggle) {
    PsiAssignmentExpression assignStmt = getAssignment(stmt);
    if (assignStmt == null) return false;
    if (!JavaTokenType.EQ.equals(assignStmt.getOperationTokenType())) return false;
    if (!ExpressionUtils.isReferenceTo(assignStmt.getLExpression(), checkVar)) return false;

    // Is the boolean being toggled?
    if (allowToggle && isNegatedReferenceTo(assignStmt.getRExpression(), checkVar)) return true;

    // Check if the boolean is set to the inverted literal value
    if (!(assignStmt.getRExpression() instanceof PsiLiteralExpression)) return false;
    if (!PsiType.BOOLEAN.equals(assignStmt.getRExpression().getType())) return false;
    Boolean tVal = (Boolean)((PsiLiteralExpression)assignStmt.getRExpression()).getValue();
    if (tVal == null || tVal.equals(initial)) return false;

    return true;
  }

  public static boolean isConstantValue(PsiExpression expr) {
    if (expr instanceof PsiLiteralExpression) return true;
    if (expr instanceof PsiReferenceExpression) {
      PsiVariable var = resolveVariable(expr);
      if (var.getModifierList() == null || !var.getModifierList().hasModifierProperty(PsiModifier.FINAL)) return false;

      if (CollectionUtils.isEmptyArray(var)) return true;

      // The type of the variable must be an immutable class, or its contents could also change at runtime
      return ClassUtils.isImmutable(var.getType());
    }
    return false;
  }

  public static boolean isVariableReferencedAfter(PsiVariable var, PsiStatement statement) {
    if (!(var instanceof PsiLocalVariable)) return false;

    // The variable must be declared inside of the same method as the statement
    if (PsiTreeUtil.getParentOfType(var, PsiLambdaExpression.class, PsiMethod.class) !=
        PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression.class, PsiMethod.class)) return false;

    PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
    if (block == null) return false;

    ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(statement.getProject())
        .getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    } catch (AnalysisCanceledException ignored) {
      return false;
    }

    // Is the variable used between the end of the loop and the end of its scope?
    final int loopEnd = controlFlow.getEndOffset(statement);
    final int blockEnd = controlFlow.getEndOffset(block);

    return ControlFlowUtil.isVariableUsed(controlFlow, loopEnd, blockEnd, var);
  }

  @Nullable
  public static PsiVariable getJoinedVariable(PsiLoopStatement loop, StreamApiMigrationInspection.TerminalBlock tb, List<PsiVariable> variables) {
    // Only works for loops with (at least one and) at most two statements [if-statement with body counts as one]
    if (tb.getStatements().length != 0 && tb.getStatements().length > 3) return null;

    // String concatenation if one variable is a StringBuffer or String
    boolean stringConcat = false;
    Optional<PsiVariable> targetVar = StreamEx.of(variables.stream())
      .findFirst(v -> v.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER));
    if (!targetVar.isPresent()) {
      targetVar = StreamEx.of(variables.stream())
        .findFirst(v -> v.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING));
      if (!targetVar.isPresent()) return null;
      stringConcat = true;

      // The concatenation target variable should retain its initial value before reaching the loop
      if (StreamApiMigrationInspection.getInitializerUsageStatus(targetVar.get(), loop) == UNKNOWN) return null;
    }

    // String concatenation with delim needs one boolean check var
    Optional<PsiVariable> checkVar = StreamEx.of(variables.stream())
      .findFirst(v -> v.getType().isAssignableFrom(PsiType.BOOLEAN));

    // If we have a boolean to check on, we must be using a delimiter
    if (!checkVar.isPresent() && tb.getStatements().length != 1) return null;

    // Initial value in case of checking on the first iteration
    boolean initVal = false;
    // Indicates if the check variable is set at the end of the loop instead of in the if statement
    boolean trailingSwitch = false;

    if (checkVar.isPresent() && tb.getStatements().length != 1) {
      // The check variable should retain its initial value before reaching the loop
      if (StreamApiMigrationInspection.getInitializerUsageStatus(checkVar.get(), loop) == UNKNOWN) return null;

      // The check variable may not be used after the loop
      if (isVariableReferencedAfter(checkVar.get(), loop)) return null;

      // Check if the check is used after it's definition
      if (tb.isReferencedInOperations(checkVar.get())) return null;

      // In case the boolean check is initialized, get the init value
      PsiExpression initializer = checkVar.get().getInitializer();
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

      // Check if-condition ordering
      boolean appendElse; // Append statement is in the else branch
      if (initVal) {
        // Check positive (check value is true)
        appendElse = ExpressionUtils.isReferenceTo(ifStmt.getCondition(), checkVar.get());
      } else {
        // Check negative (check value is false)
        appendElse = isNegatedReferenceTo(ifStmt.getCondition(), checkVar.get());
      }

      // Setup branches
      Optional<PsiCodeBlock> checkBranch = Optional.ofNullable(
        (PsiBlockStatement) (appendElse ? ifStmt.getThenBranch() : ifStmt.getElseBranch()))
        .map(PsiBlockStatement::getCodeBlock);
      Optional<PsiCodeBlock> appendBranch = Optional.ofNullable(
        (PsiBlockStatement) (appendElse ? ifStmt.getElseBranch() : ifStmt.getThenBranch()))
        .map(PsiBlockStatement::getCodeBlock);

      if (!appendBranch.isPresent()) return null;

      // Check if delimiter is correct
      if (appendBranch.get().getStatements().length != 1) return null;
      PsiExpression delim = getAppendParam(targetVar.get(), appendBranch.get().getStatements()[0], stringConcat);
      if (!TypeUtils.expressionHasTypeOrSubtype(delim, CommonClassNames.JAVA_LANG_STRING)) return null;
      if (!isConstantValue(delim)) return null;

      if (tb.getStatements().length == 2) {
        // Check correct checking on first iteration
        if (checkBranch.get().getStatements().length != 1) return null;
        if (!isValidFISetter(checkBranch.get().getStatements()[0], checkVar.get(), initVal, true)) return null;
      } else { // 3 statements in terminal block
        // If the for-loop contains 3 statements, the check variable is set at the end of the loop somewhere, not in the if-statement
        if (checkBranch.isPresent()) return null;

        trailingSwitch = true;
      }
    }

    // String init argument must not be checked, as any valid init string can be appended to the resulting string
    if (!stringConcat) {
      // The StringBuilder should be constructed with at most one argument
      if (!checkInitArguments(targetVar.get(), new String[][]{
        ArrayUtil.EMPTY_STRING_ARRAY, new String[]{CommonClassNames.JAVA_LANG_STRING}
      })) return null;
    }

    // Check if the StringBuilder is used after creation
    if (tb.isReferencedInOperations(targetVar.get())) return null;

    // The TerminalBlock must contain a single append operation
    PsiExpression appendParam = getAppendParam(targetVar.get(), tb.getStatements()[tb.getStatements().length - 1], stringConcat);
    if (!TypeUtils.expressionHasTypeOrSubtype(appendParam, CommonClassNames.JAVA_LANG_STRING)) {
      if (!trailingSwitch) return null;

      // If the check variable is not set in the if-statement, the append statement could be the either the last or the one before that
      appendParam = getAppendParam(targetVar.get(), tb.getStatements()[tb.getStatements().length - 2], stringConcat);
      if (!TypeUtils.expressionHasTypeOrSubtype(appendParam, CommonClassNames.JAVA_LANG_STRING)) return null;

      // The last statement must set the check boolean
      if (!isValidFISetter(tb.getStatements()[tb.getStatements().length - 1], checkVar.get(), initVal, false)) return null;
    } else if (trailingSwitch) {
      // The second to last statement must set the check boolean
      if (!isValidFISetter(tb.getStatements()[tb.getStatements().length - 2], checkVar.get(), initVal, false)) return null;
    }

    // The StringBuilder can't be appended to itself and the loop variable must be used to create the appended data
    if (VariableAccessUtils.variableIsUsed(targetVar.get(), appendParam)) return null;

    return targetVar.get();
  }

}
