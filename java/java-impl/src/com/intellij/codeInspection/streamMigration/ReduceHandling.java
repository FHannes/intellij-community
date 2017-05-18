package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

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
    // Search API for regex: "public (?:final ?|abstract ?)?([A-Z]\w*) \w+ ?\( ?(?:final ?)?\1 \w+ ?\)"
    addAssociative("java.awt.Rectangle", "intersection"); // Identity is R2??
    addAssociative("java.awt.Rectangle", "union"); // Identity is empty rectangle
    addAssociative(CommonClassNames.JAVA_LANG_STRING, "concat");
    addAssociative(CommonClassNames.JAVA_LANG_STRING_BUFFER, "append");
    addAssociative("java.math.BigDecimal", "add");
    addAssociative("java.math.BigDecimal", "min"); // Identity is max value?
    addAssociative("java.math.BigDecimal", "max"); // Identity is min value?
    addAssociative("java.math.BigDecimal", "multiply");
    addAssociative("java.math.BigInteger", "add");
    addAssociative("java.math.BigInteger", "and"); // Identity is ???
    addAssociative("java.math.BigInteger", "min"); // Identity is max value?
    addAssociative("java.math.BigInteger", "max"); // Identity is min value?
    addAssociative("java.math.BigInteger", "multiply");
    addAssociative("java.math.BigInteger", "or"); // Identity is ???
    addAssociative("java.math.BigInteger", "xor"); // Identity is ???
    addAssociative("java.time.Duration", "plus");
    addAssociative("java.util.StringJoiner", "merge");
    addAssociative("javax.xml.datatype.Duration", "add");
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
        if (mce == null) return null;

        // Resolve to the method declaration to verify the annotation for associativity
        PsiElement element = mce.getMethodExpression().resolve();
        if (element == null || !(element instanceof PsiMethod)) return null;
        PsiMethod operation = (PsiMethod) element;

        // Check for presence of Associative annotation
        if (!isAssociativeOperation(operation)) return null;

        // Either the call subject or the parameter of the call should be the accumulator
        if (!ExpressionUtils.isReferenceTo(mce.getMethodExpression().getQualifierExpression(), accumulator) &&
            !ExpressionUtils.isReferenceTo(mce.getArgumentList().getExpressions()[0], accumulator)) return null;

        return accumulator;
      }
    }
    return null;
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
    PsiAssignmentExpression stmt = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (stmt == null) return null;

    PsiVariable accumulator = getReductionAccumulator(stmt);
    if (!variables.contains(accumulator)) return null;

    return accumulator;
  }

}
