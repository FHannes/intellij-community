package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Frédéric Hannes
 */
public class ReduceHandling {

  @NonNls public static final String BE_KULEUVEN_CS_DTAI_ASSOCIATIVE = "be.kuleuven.cs.dtai.Associative";

  @NonNls public static final Map<String, Map<IElementType, Pair<String, Boolean>>> associativeOperators = new HashMap<>();
  @NonNls private static final Map<String, Map<String, Pair<String, Boolean>>> associativeMemberOperations = new HashMap<>();
  @NonNls private static final Map<String, Map<String, Pair<String, Boolean>>> associativeStaticOperations = new HashMap<>();

  private static void addAssociativeOperator(PsiPrimitiveType clazz, IElementType tokenType, String identity, boolean idempotent) {
    addAssociativeOperator(clazz.getBoxedTypeName(), tokenType, identity, idempotent);
    addAssociativeOperator(clazz.getCanonicalText(false), tokenType, identity, idempotent);
  }

  private static void addAssociativeOperator(String clazz, IElementType tokenType, String identity, boolean idempotent) {
    Map<IElementType, Pair<String, Boolean>> operations;
    if (associativeOperators.containsKey(clazz)) {
      operations = associativeOperators.get(clazz);
    } else {
      operations = new HashMap<>();
      associativeOperators.put(clazz, operations);
    }
    operations.put(tokenType, new Pair<>(identity, idempotent));
  }

  private static void addAssociativeMember(String clazz, String method, String identity, boolean idempotent) {
    Map<String, Pair<String, Boolean>> operations;
    if (associativeMemberOperations.containsKey(clazz)) {
      operations = associativeMemberOperations.get(clazz);
    } else {
      operations = new HashMap<>();
      associativeMemberOperations.put(clazz, operations);
    }
    operations.put(method, new Pair<>(identity, idempotent));
  }

  private static void addAssociativeStatic(String type, String clazz, String method, String identity, boolean idempotent) {
    Map<String, Pair<String, Boolean>> operations;
    if (associativeStaticOperations.containsKey(type)) {
      operations = associativeStaticOperations.get(type);
    } else {
      operations = new HashMap<>();
      associativeStaticOperations.put(type, operations);
    }
    operations.put(clazz + '.' + method, new Pair<>(identity, idempotent));
  }

  // TODO: Support unary and binary
  // TODO: Identify idempotent operations for patternµ
  // TODO: Ensure types match for operators (primitive != classtype)

  static {
    // boolean
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.AND, "true", true);
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.OR, "false", true);
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.ANDAND, "true", false);
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.OROR, "false", true);

    // char
    addAssociativeOperator(PsiType.CHAR, JavaTokenType.PLUS, "0", false);
    addAssociativeOperator(PsiType.CHAR, JavaTokenType.ASTERISK, "1", false);
    addAssociativeOperator(PsiType.CHAR, JavaTokenType.AND, "1", true);
    addAssociativeOperator(PsiType.CHAR, JavaTokenType.OR, "0", true);
    addAssociativeOperator(PsiType.CHAR, JavaTokenType.XOR, "", false);

    // byte
    addAssociativeOperator(PsiType.BYTE, JavaTokenType.PLUS, "0", false);
    addAssociativeOperator(PsiType.BYTE, JavaTokenType.ASTERISK, "1", false);
    addAssociativeOperator(PsiType.BYTE, JavaTokenType.AND, "$FF", true);
    addAssociativeOperator(PsiType.BYTE, JavaTokenType.OR, "0", true);
    addAssociativeOperator(PsiType.BYTE, JavaTokenType.XOR, "", false);

    // short
    addAssociativeOperator(PsiType.SHORT, JavaTokenType.PLUS, "0", false);
    addAssociativeOperator(PsiType.SHORT, JavaTokenType.ASTERISK, "1", false);
    addAssociativeOperator(PsiType.SHORT, JavaTokenType.AND, "", true);
    addAssociativeOperator(PsiType.SHORT, JavaTokenType.OR, "0", true);
    addAssociativeOperator(PsiType.SHORT, JavaTokenType.XOR, "", false);

    // int
    addAssociativeOperator(PsiType.INT, JavaTokenType.PLUS, "0", false);
    addAssociativeOperator(PsiType.INT, JavaTokenType.ASTERISK, "1", false);
    addAssociativeOperator(PsiType.INT, JavaTokenType.AND, "", true);
    addAssociativeOperator(PsiType.INT, JavaTokenType.OR, "0", true);
    addAssociativeOperator(PsiType.INT, JavaTokenType.XOR, "", false);

    // long
    addAssociativeOperator(PsiType.LONG, JavaTokenType.PLUS, "0", false);
    addAssociativeOperator(PsiType.LONG, JavaTokenType.ASTERISK, "1", false);
    addAssociativeOperator(PsiType.LONG, JavaTokenType.AND, "", true);
    addAssociativeOperator(PsiType.LONG, JavaTokenType.OR, "0L", true);
    addAssociativeOperator(PsiType.LONG, JavaTokenType.XOR, "", false);

    // float
    addAssociativeOperator(PsiType.FLOAT, JavaTokenType.PLUS, "0F", false);
    addAssociativeOperator(PsiType.FLOAT, JavaTokenType.ASTERISK, "1F", false);

    // double
    addAssociativeOperator(PsiType.FLOAT, JavaTokenType.PLUS, "0D", false);
    addAssociativeOperator(PsiType.FLOAT, JavaTokenType.ASTERISK, "1D", false);

    // String
    addAssociativeOperator(CommonClassNames.JAVA_LANG_STRING, JavaTokenType.PLUS, "\"\"", false);

    // Member method operations
    addAssociativeMember(CommonClassNames.JAVA_LANG_STRING, "concat", "\"\"", false);
    addAssociativeMember("java.math.BigDecimal", "add", "java.math.BigDecimal.ZERO", false);
    addAssociativeMember("java.math.BigDecimal", "min", "", true);
    addAssociativeMember("java.math.BigDecimal", "max", "", true);
    addAssociativeMember("java.math.BigDecimal", "multiply", "java.math.BigDecimal.ONE", false);
    addAssociativeMember("java.math.BigInteger", "add", "java.math.BigInteger.ZERO", false);
    addAssociativeMember("java.math.BigInteger", "and", "", true);
    addAssociativeMember("java.math.BigInteger", "min", "", true);
    addAssociativeMember("java.math.BigInteger", "max", "", true);
    addAssociativeMember("java.math.BigInteger", "multiply", "java.math.BigInteger.ONE", false);
    addAssociativeMember("java.math.BigInteger", "or", "java.math.BigInteger.ZERO", true);
    addAssociativeMember("java.math.BigInteger", "xor", "", false);
    addAssociativeMember("java.time.Duration", "plus", "java.time.Duration.ZERO", false);
    addAssociativeMember("javax.xml.datatype.Duration", "add", "", false);

    // public static (?:final )?(\w\w*) \w+ ?\( ?(?:final )?\1 \w+ ?, ?(?:final )?\1 \w+ ?\)
    // Static method operations
    addAssociativeStatic(PsiKeyword.BOOLEAN, CommonClassNames.JAVA_LANG_BOOLEAN, "logicalAnd", "true", true);
    addAssociativeStatic(PsiKeyword.BOOLEAN, CommonClassNames.JAVA_LANG_BOOLEAN, "logicalOr", "false", true);
    addAssociativeStatic(PsiKeyword.BOOLEAN, CommonClassNames.JAVA_LANG_BOOLEAN, "logicalXor", "", false);
    addAssociativeStatic(PsiKeyword.DOUBLE, CommonClassNames.JAVA_LANG_DOUBLE, "max", "", true);
    addAssociativeStatic(PsiKeyword.DOUBLE, CommonClassNames.JAVA_LANG_DOUBLE, "min", "", true);
    addAssociativeStatic(PsiKeyword.DOUBLE, CommonClassNames.JAVA_LANG_DOUBLE, "sum", "0D", false);
    addAssociativeStatic(PsiKeyword.FLOAT, CommonClassNames.JAVA_LANG_FLOAT, "max", "", true);
    addAssociativeStatic(PsiKeyword.FLOAT, CommonClassNames.JAVA_LANG_FLOAT, "min", "", true);
    addAssociativeStatic(PsiKeyword.FLOAT, CommonClassNames.JAVA_LANG_FLOAT, "sum", "0F", false);
    addAssociativeStatic(PsiKeyword.INT, CommonClassNames.JAVA_LANG_INTEGER, "max", "", true);
    addAssociativeStatic(PsiKeyword.INT, CommonClassNames.JAVA_LANG_INTEGER, "min", "", true);
    addAssociativeStatic(PsiKeyword.INT, CommonClassNames.JAVA_LANG_INTEGER, "sum", "0", false);
    addAssociativeStatic(PsiKeyword.LONG, CommonClassNames.JAVA_LANG_LONG, "max", "", true);
    addAssociativeStatic(PsiKeyword.LONG, CommonClassNames.JAVA_LANG_LONG, "min", "", true);
    addAssociativeStatic(PsiKeyword.LONG, CommonClassNames.JAVA_LANG_LONG, "sum", "0L", false);
    addAssociativeStatic(PsiKeyword.DOUBLE, CommonClassNames.JAVA_LANG_MATH, "max", "", true);
    addAssociativeStatic(PsiKeyword.FLOAT, CommonClassNames.JAVA_LANG_MATH, "max", "", true);
    addAssociativeStatic(PsiKeyword.INT, CommonClassNames.JAVA_LANG_MATH, "max", "", true);
    addAssociativeStatic(PsiKeyword.LONG, CommonClassNames.JAVA_LANG_MATH, "max", "", true);
    addAssociativeStatic(PsiKeyword.DOUBLE, CommonClassNames.JAVA_LANG_MATH, "min", "", true);
    addAssociativeStatic(PsiKeyword.FLOAT, CommonClassNames.JAVA_LANG_MATH, "min", "", true);
    addAssociativeStatic(PsiKeyword.LONG, CommonClassNames.JAVA_LANG_MATH, "min", "", true);
    addAssociativeStatic(PsiKeyword.LONG, CommonClassNames.JAVA_LANG_MATH, "min", "", true);
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
    Set<String> methods = associativeMemberOperations.get(clazz.getQualifiedName());
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
