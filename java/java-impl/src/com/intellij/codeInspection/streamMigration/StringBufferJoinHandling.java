package com.intellij.codeInspection.streamMigration;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

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
    if (call.getArgumentList().getExpressions().length != 1) return false;
    if (!ExpressionUtils.isReferenceTo(call.getArgumentList().getExpressions()[0], tb.getVariable())) return false;
    if (!"append".equals(call.getMethodExpression().getReferenceName())) return false;
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (qualifierExpression == null || qualifierExpression instanceof PsiThisExpression) {
      PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
      return method == null || !method.getName().equals("append");
    }
    return !(qualifierExpression instanceof PsiMethodCallExpression);
  }

  public static PsiVariable extractStringBuilder(PsiMethodCallExpression call) {
    // Resolve to object call is made on
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression) qualifierExpression).resolve();
    // Check if variable is StringBuilder
    if (!(resolved instanceof PsiVariable)) return null;
    PsiVariable var = (PsiVariable) resolved;
    if (!var.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER)) return null;
    return var;
  }

  public static PsiMethodCallExpression getToStringCall(StreamApiMigrationInspection.TerminalBlock tb, PsiVariable sb) {
    if (tb.isEmpty()) return null;

    //tb.getLastOperation();
    return null;
  }

  public static PsiExpression getSingleExprParam(PsiMethodCallExpression call) {
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length != 1 || !(args[0] instanceof PsiReferenceExpression)) return null;
    return args[0];
  }

  public static PsiVariable getSingleVarParam(PsiMethodCallExpression call) {
    PsiExpression expr = getSingleExprParam(call);
    if (expr == null) return null;
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
  private static boolean checkInitMaxArguments(PsiVariable var, int max) {
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
    return initNew.getArgumentList() != null && initNew.getArgumentList().getExpressions().length <= max;
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

    if (switchVar.isPresent()) {
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

      // TODO: Check valid delim switching and extract delim to verify type
    } else {
      // If there's no switch boolean, the concat operation can't have a delimiter, so it must have a single statement in the loop
      if (tb.getStatements().length != 1) return null;
    }

    // The StringBuilder should be constructed with at most one argument
    if (!checkInitMaxArguments(sbVar.get(), 1)) return null;

    // Check if the StringBuilder is used after creation
    if (tb.isReferencedInOperations(sbVar.get())) return null;

    // The terminalBlock must contain a single append operation
    PsiMethodCallExpression appendCall = tb.getSingleExpression(PsiMethodCallExpression.class);
    if (appendCall == null || !isAppendCall(tb, appendCall)) return null;
    // The append operation must be called on the StringBuilder
    PsiVariable callObj = extractStringBuilder(appendCall);
    if (!sbVar.equals(callObj)) return null;

    return sbVar.get();

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
