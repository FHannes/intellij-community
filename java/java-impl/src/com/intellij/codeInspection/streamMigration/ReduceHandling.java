package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Frédéric Hannes
 */
public class ReduceHandling {

  @NonNls public static final String BE_KULEUVEN_CS_DTAI_ASSOCIATIVE = "be.kuleuven.cs.dtai.Associative";
  @NonNls public static final String BE_KULEUVEN_CS_DTAI_IMMUTABLE = "be.kuleuven.cs.dtai.Immutable";

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

  static {
    // boolean
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.AND, "true", true);
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.OR, "false", true);
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.ANDAND, "true", false);
    addAssociativeOperator(PsiType.BOOLEAN, JavaTokenType.OROR, "false", true);

    /*// char
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
    addAssociativeOperator(PsiType.SHORT, JavaTokenType.XOR, "", false);*/

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
    addAssociativeOperator(PsiType.DOUBLE, JavaTokenType.PLUS, "0D", false);
    addAssociativeOperator(PsiType.DOUBLE, JavaTokenType.ASTERISK, "1D", false);

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

  @Nullable
  public static IElementType mapAssignOperator(IElementType elementType) {
    if (JavaTokenType.ANDEQ.equals(elementType)) {
      return JavaTokenType.AND;
    } else if (JavaTokenType.ASTERISKEQ.equals(elementType)) {
      return JavaTokenType.ASTERISK;
    } else if (JavaTokenType.OREQ.equals(elementType)) {
      return JavaTokenType.OR;
    } else if (JavaTokenType.PLUSEQ.equals(elementType)) {
      return JavaTokenType.PLUS;
    } else if (JavaTokenType.XOREQ.equals(elementType)) {
      return JavaTokenType.XOR;
    } else {
      return null;
    }
  }

  @Nullable
  public static String operatorToString(IElementType elementType) {
    if (JavaTokenType.AND.equals(elementType)) {
      return "&";
    } else if (JavaTokenType.ANDAND.equals(elementType)) {
      return "&&";
    } else if (JavaTokenType.ASTERISK.equals(elementType)) {
      return "*";
    } else if (JavaTokenType.OR.equals(elementType)) {
      return "|";
    } else if (JavaTokenType.OROR.equals(elementType)) {
      return "||";
    } else if (JavaTokenType.PLUS.equals(elementType)) {
      return "+";
    } else if (JavaTokenType.XOR.equals(elementType)) {
      return "^";
    } else {
      return null;
    }
  }

  private static Pair<String, Boolean> getAssociativeOperation(PsiMethod method) {
    PsiElement parent = method.getParent();
    if (!(parent instanceof PsiClass)) return null;
    PsiClass clazz = (PsiClass) parent;
    if (clazz.getQualifiedName() == null) return null;

    Map<String, Pair<String, Boolean>> methodData;
    if (isStatic(method)) {
      if (method.getParameterList().getParametersCount() != 2) return null;
      if (method.getReturnType() == null) return null;

      methodData = associativeStaticOperations.get(method.getReturnType().getCanonicalText());

      String methodRef = clazz.getQualifiedName() + '.' + method.getName();

      if (methodData != null && methodData.containsKey(methodRef)) return methodData.get(methodRef);
    } else {
      if (method.getParameterList().getParametersCount() != 1) return null;

      // Method parameter & result type should be the same type as the method class
      PsiTypeElement paramType = method.getParameterList().getParameters()[0].getTypeElement();
      if (paramType == null || !paramType.getType().equalsToText(clazz.getQualifiedName())) return null;
      if (method.getReturnType() == null || !method.getReturnType().equalsToText(clazz.getQualifiedName())) return null;

      methodData = associativeMemberOperations.get(clazz.getQualifiedName());

      if (methodData != null && methodData.containsKey(method.getName())) return methodData.get(method.getName());
    }

    if (!ClassUtils.isImmutable(method.getReturnType())) {
      if (!(method.getReturnType() instanceof PsiClassType)) return null;

      PsiClass returnClass = ((PsiClassType) method.getReturnType()).resolve();
      if (returnClass == null || !AnnotationUtil.isAnnotated(returnClass, Collections.singletonList(BE_KULEUVEN_CS_DTAI_IMMUTABLE),
                                                             false, true)) return null;
    }

    // Check for presence of Associative annotation
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, true, BE_KULEUVEN_CS_DTAI_ASSOCIATIVE);
    if (annotation == null) return null;

    return Pair.create(AnnotationUtil.getStringAttributeValue(annotation, "identity"),
                       AnnotationUtil.getBooleanAttributeValue(annotation, "idempotent"));
  }

  @Nullable
  public static PsiVariable resolveVariable(PsiExpression expr) {
    if (!(expr instanceof PsiReferenceExpression)) return null;
    PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
    if (!(resolved instanceof PsiVariable)) return null;
    return (PsiVariable) resolved;
  }

  public static boolean isStatic(PsiModifierListOwner element) {
    return element.getModifierList() != null && element.getModifierList().hasModifierProperty(PsiModifier.STATIC);
  }

  public static class ReductionData {
    private PsiVariable accumulator;
    private PsiExpression expression;
    private Pair<String, Boolean> operatorData;
    private String format;
    private boolean reversed;

    public ReductionData(PsiVariable accumulator,
                         PsiExpression expression,
                         Pair<String, Boolean> operatorData, String format, boolean reversed) {
      this.accumulator = accumulator;
      this.expression = expression;
      this.operatorData = operatorData;
      this.format = format;
      this.reversed = reversed;
    }

    public PsiVariable getAccumulator() {
      return accumulator;
    }

    public PsiExpression getExpression() {
      return expression;
    }

    public Pair<String, Boolean> getOperatorData() {
      return operatorData;
    }

    public String getFormat() {
      return format;
    }

    public boolean isReversed() {
      return reversed;
    }
  }

  public static boolean isTypeAllowedForReduce(PsiVariable accumulator, PsiType type) {
    PsiType accType = accumulator.getType();
    if (type instanceof PsiPrimitiveType) {
      PsiPrimitiveType ppt = PsiPrimitiveType.getUnboxedType(accType);
      if (ppt != null && ppt.equals(type)) {
        return true;
      }
    }
    return accType.equals(type);
  }

  @Nullable
  public static ReductionData getReductionAccumulator(PsiAssignmentExpression assignment) {
    if (!(assignment.getLExpression() instanceof PsiReferenceExpression)) return null;
    final PsiVariable accumulator = resolveVariable(assignment.getLExpression());
    if (accumulator == null) return null;
    final PsiType type = accumulator.getType();

    PsiExpression expr1 = null, expr2 = null;
    Pair<String, Boolean> opData = null;
    String format = "";

    IElementType op = mapAssignOperator(assignment.getOperationTokenType());

    if (op != null) {
      if (!associativeOperators.containsKey(type.getCanonicalText())) return null;
      opData = associativeOperators.get(type.getCanonicalText()).get(op);
      if (opData == null) return null;

      expr1 = assignment.getLExpression();
      expr2 = assignment.getRExpression();

      if (expr2 == null || !isTypeAllowedForReduce(accumulator, expr2.getType())) return null;

      format = "%s " + operatorToString(op) + " %s";
    } else if (JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      if (assignment.getRExpression() instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)assignment.getRExpression();
        op = binOp.getOperationTokenType();

        if (!associativeOperators.containsKey(type.getCanonicalText())) return null;
        opData = associativeOperators.get(type.getCanonicalText()).get(op);
        if (opData == null) return null;

        expr1 = binOp.getLOperand();
        expr2 = binOp.getROperand();

        if (!isTypeAllowedForReduce(accumulator, expr1.getType())) return null;
        if (expr2 == null || !isTypeAllowedForReduce(accumulator, expr2.getType())) return null;

        format = "%s " + operatorToString(op) + " %s";
      } else if (assignment.getRExpression() instanceof PsiMethodCallExpression) {
        // Check that accumulator is valid as a method call
        PsiMethodCallExpression mce = (PsiMethodCallExpression) assignment.getRExpression();
        if (mce == null) return null;

        // Resolve to the method declaration to verify the annotation for associativity
        PsiElement element = mce.getMethodExpression().resolve();
        if (element == null || !(element instanceof PsiMethod)) return null;
        PsiMethod method = (PsiMethod) element;

        // Operation must be method defined in a class
        PsiElement parent = method.getParent();
        if (!(parent instanceof PsiClass)) return null;

        // Method must have a return value with type same as accumulator
        if (method.getReturnType() == null || !isTypeAllowedForReduce(accumulator, method.getReturnType())) return null;

        // Method must have more than 1 parameter
        if (method.getParameterList().getParametersCount() == 0) return null;

        // First method parameter must be same type as accumulator
        if (!isTypeAllowedForReduce(accumulator, method.getParameterList().getParameters()[0].getType())) return null;

        if (isStatic(method)) {
          // Static methods have 2 params
          if (method.getParameterList().getParametersCount() != 2) return null;

          // Second method parameter must be same type as accumulator
          if (!isTypeAllowedForReduce(accumulator, method.getParameterList().getParameters()[1].getType())) return null;

          expr1 = ParenthesesUtils.stripParentheses(mce.getArgumentList().getExpressions()[0]);
          expr2 = ParenthesesUtils.stripParentheses(mce.getArgumentList().getExpressions()[1]);
        } else {
          // Non-static methods have 1 param
          if (method.getParameterList().getParametersCount() != 1) return null;

          expr1 = ParenthesesUtils.stripParentheses(mce.getMethodExpression().getQualifierExpression());
          expr2 = ParenthesesUtils.stripParentheses(mce.getArgumentList().getExpressions()[0]);
        }

        if (expr1 == null || !isTypeAllowedForReduce(accumulator, expr1.getType())) return null;
        if (expr2 == null || !isTypeAllowedForReduce(accumulator, expr2.getType())) return null;

        opData = getAssociativeOperation(method);
        if (opData == null) return null;

        if (isStatic(method)) {
          format = ((PsiClass)parent).getQualifiedName() + '.' + method.getName() + "(%s, %s)";
        } else {
          format = "%s." + method.getName() + "(%s)";
        }
      } else return null;
    } else return null;

    // If there's no identity for the reduce, the function MUST be idempotent
    if (opData.getFirst().isEmpty() && !opData.getSecond()) return null;

    boolean reversed;
    PsiExpression returnExpr;

    boolean ref1 = ExpressionUtils.isReferenceTo(expr1, accumulator);
    boolean ref2 = ExpressionUtils.isReferenceTo(expr2, accumulator);
    if (ref1 & !ref2) {
      reversed = false;
      returnExpr = expr2;
    } else if (ref2 & !ref1) {
      reversed = true;
      returnExpr = expr1;
    } else return null;

    return new ReductionData(accumulator, returnExpr, opData, format, reversed);
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
  public static ReduceHandling.ReductionData getReduceVar(PsiLoopStatement loop, StreamApiMigrationInspection.TerminalBlock tb, List<PsiVariable> variables) {
    PsiAssignmentExpression stmt = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (stmt == null) return null;

    ReduceHandling.ReductionData data = getReductionAccumulator(stmt);
    if (data == null || !variables.contains(data.getAccumulator())) return null;

    return data;
  }

}
