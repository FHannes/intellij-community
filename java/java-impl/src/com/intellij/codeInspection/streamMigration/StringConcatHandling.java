package com.intellij.codeInspection.streamMigration;

import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN;

/**
 * @author Frédéric Hannes
 */
public class StringConcatHandling {

  @NonNls public static final String JAVA_LANG_CHARSEQUENCE = "java.lang.CharSequence";

  @Nullable
  public static PsiVariable resolveVariable(PsiExpression expr) {
    if (!(expr instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
    if (!(resolved instanceof PsiVariable)) return null;
    return (PsiVariable) resolved;
  }

  public static PsiVariable getConcatVariable(PsiStatement stmt) {
    PsiMethodCallExpression mce = getMethodCall(stmt);
    if (mce != null) {
      PsiVariable var = resolveVariable(mce.getMethodExpression().getQualifierExpression());
      return var != null && var.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER) ? var : null;
    }

    PsiAssignmentExpression ae = getAssignment(stmt);
    if (ae != null) {
      PsiVariable var = StreamApiMigrationInspection.extractAccumulator(ae);
      return var != null && var.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING) ? var : null;
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
   * Returns the variable which is used to check for the first execution of an iterated code block, in the given if-condition which
   * originates at the start of that code block.
   *
   * @param ifStmt
   * @return
   */
  @Nullable
  public static PsiVariable getCheckVariable(PsiIfStatement ifStmt) {
    PsiExpression ifCond = ParenthesesUtils.stripParentheses(ifStmt.getCondition());
    if (ifCond instanceof PsiPrefixExpression) {
      ifCond = ((PsiPrefixExpression) ifCond).getOperand();
    }
    if (!(ifCond instanceof PsiReferenceExpression)) return null;
    PsiElement elem = ((PsiReferenceExpression) ifCond).resolve();
    // The variable must be local!
    if (!(elem instanceof PsiLocalVariable)) return null;
    return (PsiVariable) elem;
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
  public static boolean isValidCheckSetter(PsiStatement stmt, PsiVariable checkVar, boolean initial, boolean allowToggle) {
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

  public static boolean isFinal(PsiModifierListOwner element) {
    return element.getModifierList() != null && element.getModifierList().hasModifierProperty(PsiModifier.FINAL);
  }

  public static boolean isConstantValue(PsiExpression expr, PsiLoopStatement loop) {
    if (expr instanceof PsiLiteralExpression) return true;

    if (ExpressionUtils.isZeroLengthArrayConstruction(expr)) return true;

    if (expr instanceof PsiReferenceExpression) {
      PsiVariable var = resolveVariable(expr);
      if (var == null) return false;

      boolean fnl = isFinal(var);

      if (fnl && CollectionUtils.isEmptyArray(var)) return true;

      if (!fnl && isValueChangedBetween(var, loop)) return false;

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

  public static boolean isValueChangedBetween(PsiVariable var, PsiStatement statement) {
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

    final int loopStart = controlFlow.getStartOffset(statement);
    final int loopEnd = controlFlow.getEndOffset(statement);

    Map<Integer, List<ControlFlowUtil.ControlFlowEdge>> edges =
      StreamEx.of(ControlFlowUtil.getEdges(controlFlow, loopStart)).groupingBy(e -> e.myFrom);

    Queue<Integer> branches = new LinkedList<>();
    branches.add(loopStart);
    while (!branches.isEmpty()) {
      int branch = branches.poll();

      if (branch < controlFlow.getSize() && branch <= loopEnd) {
        if (isVariableWritten(controlFlow, branch, var)) return true;

        branches.addAll(StreamEx.of(edges.get(branch)).map(edge -> edge.myTo).toList());
      }
    }

    return false;
  }

  public static boolean isValueReferencedAfter(PsiVariable var, PsiStatement statement) {
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

    Map<Integer, List<ControlFlowUtil.ControlFlowEdge>> edges =
      StreamEx.of(ControlFlowUtil.getEdges(controlFlow, loopEnd)).groupingBy(e -> e.myFrom);

    Queue<Integer> branches = new LinkedList<>();
    branches.add(loopEnd);
    while (!branches.isEmpty()) {
      int branch = branches.poll();

      if (branch < controlFlow.getSize()) {
        if (isVariableRead(controlFlow, branch, var)) return true;

        // Branch stays alive as long as the value isn't overwritten
        if (!isVariableWritten(controlFlow, branch, var) && edges.containsKey(branch)) {
          branches.addAll(StreamEx.of(edges.get(branch)).map(edge -> edge.myTo).toList());
        }
      }
    }

    return false;
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
  public static PsiVariable getJoinedVariable(PsiLoopStatement loop, StreamApiMigrationInspection.TerminalBlock tb, List<PsiVariable> variables) {
    // Only works for loops with (at least one and) at most two statements [if-statement with body counts as one]
    if (tb.getStatements().length != 0 && tb.getStatements().length > 3) return null;

    // Accumulator variable used to concat the delimiter
    PsiVariable delimVar = null;
    // Check variable used to check if the concatenation is in its first operation
    PsiVariable checkVar = null;
    // Initial value in case of checking on the first iteration
    boolean initVal = false;
    // Indicates if the check variable is set at the end of the loop instead of in the if statement
    boolean trailingSwitch = false;

    if (tb.getStatements().length != 1) {
      // The first statement in the loop must be an if-statement
      if (!(tb.getStatements()[0] instanceof PsiIfStatement)) return null;
      PsiIfStatement ifStmt = (PsiIfStatement) tb.getStatements()[0];

      // The check variable must be used in the if-statement and be a valid non-final stream variable
      checkVar = getCheckVariable(ifStmt);
      if (checkVar == null || isFinal(checkVar)) return null;

      // The check variable should retain its initial value before reaching the loop
      if (StreamApiMigrationInspection.getInitializerUsageStatus(checkVar, loop) == UNKNOWN) return null;

      // The check variable's value may not be used after the loop
      if (isValueReferencedAfter(checkVar, loop)) return null;

      // Check if the check is used after it's definition
      if (tb.isReferencedInOperations(checkVar)) return null;

      // In case the boolean check is initialized, get the init value
      PsiExpression initializer = checkVar.getInitializer();
      if (initializer instanceof PsiLiteralExpression) {
        if (PsiType.BOOLEAN.equals(initializer.getType())) {
          Boolean tVal = (Boolean)((PsiLiteralExpression)initializer).getValue();
          if (tVal == null) return null; // Value can not be set to null
          initVal = tVal;
        }
      }

      // Check if-condition ordering
      boolean appendElse; // Append statement is in the else branch
      if (initVal) {
        // Check positive (check value is true)
        appendElse = ExpressionUtils.isReferenceTo(ifStmt.getCondition(), checkVar);
      } else {
        // Check negative (check value is false)
        appendElse = isNegatedReferenceTo(ifStmt.getCondition(), checkVar);
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
      delimVar = getConcatVariable(appendBranch.get().getStatements()[0]);
      if (delimVar == null) return null;
      PsiExpression delim = getAppendParam(delimVar, appendBranch.get().getStatements()[0],
                                           delimVar.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING));
      if (!isConstantValue(delim, loop)) return null;

      if (tb.getStatements().length == 2) {
        if (!checkBranch.isPresent()) return null;
        // Check correct checking on first iteration
        if (checkBranch.get().getStatements().length != 1) return null;
        if (!isValidCheckSetter(checkBranch.get().getStatements()[0], checkVar, initVal, true)) return null;
      } else { // 3 statements in terminal block
        // If the for-loop contains 3 statements, the check variable is set at the end of the loop somewhere, not in the if-statement
        if (checkBranch.isPresent()) return null;

        trailingSwitch = true;
      }
    }

    // Statement index where the append occurs
    int appendIndex = 1;

    // Determine concat variable
    PsiVariable concatVar = getConcatVariable(tb.getStatements()[tb.getStatements().length - appendIndex]);
    if (concatVar == null) {
      if (!trailingSwitch) return null;

      concatVar = getConcatVariable(tb.getStatements()[tb.getStatements().length - ++appendIndex]);
      if (concatVar == null) return null;
    }

    // With plain string concatenation, the variable can't be used before the terminal block
    if (concatVar.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      if (StreamApiMigrationInspection.getInitializerUsageStatus(concatVar, loop) == UNKNOWN) return null;
    }

    // Check if the concat accumulator is used after creation
    if (tb.isReferencedInOperations(concatVar)) return null;

    // Check if concat accumulator is the same one used for the delimiter
    if (delimVar != null && !delimVar.equals(concatVar)) return null;

    // The concat accumulator must be a valid stream variable
    if (!variables.contains(concatVar)) return null;

    // The TerminalBlock must contain a single append operation
    PsiExpression appendParam = getAppendParam(concatVar, tb.getStatements()[tb.getStatements().length - appendIndex],
                                               concatVar.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING));

    // Verify that the check variable is assigned at the end of the loop if required
    if (trailingSwitch && !isValidCheckSetter(tb.getStatements()[appendIndex], checkVar, initVal, false)) return null;

    // The StringBuilder can't be appended to itself and the loop variable must be used to create the appended data
    if (VariableAccessUtils.variableIsUsed(concatVar, appendParam)) return null;

    return concatVar;
  }

}
