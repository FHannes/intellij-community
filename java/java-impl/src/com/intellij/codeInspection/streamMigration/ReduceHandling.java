package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN;

/**
 * @author Frédéric Hannes
 */
public class ReduceHandling {

  @NonNls public static final String BE_KULEUVEN_CS_DTAI_ASSOCIATIVE = "be.kuleuven.cs.dtai.Associative";

  @NonNls private static final Map<String, Set<String>> associativeOperations = new HashMap<>();

  private static void addAssociative(String clazz, String method) {
    Set<String> operations;
    if (associativeOperations.containsKey(clazz)) {
      operations = associativeOperations.get(clazz);
    } else {
      operations = new HashSet<>();
      associativeOperations.put(clazz, operations);
    }
    operations.add(method);
  }

  static {
    addAssociative(CommonClassNames.JAVA_LANG_STRING, "concat");
  }

  private static boolean isAssociativeOperation(PsiMethod method) {
    // Associative operations executed on an object only!
    PsiElement parent = method.getParent();
    if (!(parent instanceof PsiClass)) return false;

    // Check that the method just has a single parameter of the type of its parent class
    PsiClass clazz = (PsiClass) parent;
    if (clazz.getQualifiedName() == null) return false;
    if (method.getParameterList().getParametersCount() != 1) return false;
    PsiTypeElement paramType = method.getParameterList().getParameters()[0].getTypeElement();
    if (paramType == null || !paramType.getType().equalsToText(clazz.getQualifiedName())) return false;

    // Is known associative?
    Set<String> methods = associativeOperations.get(clazz.getQualifiedName());
    if (methods != null && methods.contains(method.getName())) return true;

    // Check for presence of Associative annotation
    if (AnnotationUtil.isAnnotated(method, Collections.singletonList(BE_KULEUVEN_CS_DTAI_ASSOCIATIVE), false, true)) return true;

    return false;
  }

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
        // Check that accumulator is valid as a method call on the accumulator variable
        PsiMethodCallExpression mce = (PsiMethodCallExpression) assignment.getRExpression();
        if (mce == null || !ExpressionUtils.isReferenceTo(mce.getMethodExpression().getQualifierExpression(), accumulator)) return null;

        // Resolve to the method declaration to verify the annotation for associativity
        PsiElement element = mce.getMethodExpression().resolve();
        if (element == null || !(element instanceof PsiMethod)) return null;
        PsiMethod operation = (PsiMethod) element;

        // Check for presence of Associative annotation
        if (!isAssociativeOperation(operation)) return null;

        return accumulator;
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

  @Nullable
  public static PsiVariable getReduceVar(PsiLoopStatement loop, StreamApiMigrationInspection.TerminalBlock tb, List<PsiVariable> variables) {
    PsiAssignmentExpression stmt = getAssignment(tb.getSingleStatement());
    if (stmt == null) return null;

    PsiVariable accumulator = getReductionAccumulator(stmt);
    if (!variables.contains(accumulator)) return null;

    // Variable can't be used after declaration
    // TODO: allow variable to be used after declaration, by using current value as init
    if (StreamApiMigrationInspection.getInitializerUsageStatus(accumulator, loop) == UNKNOWN) return null;

    return accumulator;
  }

}
