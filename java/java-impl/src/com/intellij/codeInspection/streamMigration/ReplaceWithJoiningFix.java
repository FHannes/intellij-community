package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * @author Frédéric Hannes
 */
class ReplaceWithJoiningFix extends MigrateToStreamFix {

  private boolean stringConcat;

  public ReplaceWithJoiningFix(boolean stringConcat) {
    this.stringConcat = stringConcat;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with joining()";
  }

  private static PsiElement replaceWithStringConcatenation(@NotNull Project project,
                                                           PsiLoopStatement loopStatement,
                                                           PsiVariable var,
                                                           PsiVariable checkVar,
                                                           StringBuilder builder,
                                                           boolean stringConcat) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    restoreComments(loopStatement, loopStatement.getBody());
    ControlFlowUtils.InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);
    if (stringConcat && !status.equals(ControlFlowUtils.InitializerUsageStatus.UNKNOWN) &&
        !status.equals(ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE)) {
      // Get initializer constructor argument if present
      String sbArg = Optional.ofNullable(var.getInitializer()).map(PsiElement::getText).orElse(null);
      if (sbArg != null && !"\"\"".equals(sbArg)) {
        builder.insert(0, " + ");
        builder.insert(0, sbArg);
      }
    }

    // Replace initializer
    if (var.getTypeElement() != null) {
      if (checkVar != null) {
        if (StringConcatHandling.isVariableReferencedAfter(checkVar, loopStatement)) {
          PsiExpression initializer = checkVar.getInitializer();
          if (initializer != null) {
            initializer.delete();
          }
        } else {
          checkVar.delete();
        }
        // Refresh status after removing switch variable
        status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);
      }
      if (stringConcat) {
        if (!status.equals(ControlFlowUtils.InitializerUsageStatus.UNKNOWN) &&
            !status.equals(ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE)) {
          PsiExpression initializer = var.getInitializer();
          return replaceInitializer(loopStatement, var, initializer, builder.toString(), status);
        } else {
          return loopStatement.replace(elementFactory.createStatementFromText(var.getName() + " += " + builder.toString() + ";",
                                                                              loopStatement));
        }
      } else {
        return loopStatement.replace(elementFactory.createStatementFromText(var.getName() + ".append(" + builder.toString() + ");",
                                                                            loopStatement));
      }
    }
    return loopStatement;
  }

  @Override
  PsiElement migrate(@NotNull Project project,
                     @NotNull PsiLoopStatement loopStatement,
                     @NotNull PsiStatement body,
                     @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    tb = tb.tryPeelLimit(loopStatement);

    Optional<PsiVariable> var;
    if (stringConcat) {
      var = StreamEx.of(tb.getStatements())
        .map(StringConcatHandling::getAssignment)
        .nonNull().map(StreamApiMigrationInspection::extractAccumulator)
        .nonNull().filter(v -> v.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING))
        .findAny();
    } else {
      var = StreamEx.of(tb.getStatements())
        .map(StringConcatHandling::getMethodCall)
        .nonNull().map(e -> StringConcatHandling.resolveVariable(e.getMethodExpression().getQualifierExpression()))
        .nonNull().filter(v -> v.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER))
        .findAny();
    }
    if (!var.isPresent()) return null;

    Optional<PsiExpression> appended = StreamEx.of(tb.getStatements())
      .map(s -> StringConcatHandling.getAppendParam(var.get(), s, stringConcat))
      .nonNull().findAny();
    if (!appended.isPresent()) return null;

    PsiVariable checkVar = null;

    PsiType type = TypeUtils.getType(CommonClassNames.JAVA_LANG_STRING, body);
    StreamApiMigrationInspection.Operation op = tb.getLastOperation();
    if (!TypeUtils.expressionHasTypeOrSubtype(appended.get(), StringConcatHandling.JAVA_LANG_CHARSEQUENCE)) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      PsiExpression newAppended = elementFactory.createExpressionFromText(CommonClassNames.JAVA_LANG_STRING + ".valueOf(" +
                                                                       appended.get().getText() + ")", loopStatement);

      op = new MapOp(op, newAppended, tb.getVariable(), type);
    } else {
      op = new MapOp(op, appended.get(), tb.getVariable(), type);
    }

    StringBuilder builder = generateStream(op);
    builder.append(".collect(java.util.stream.Collectors.joining(");
    if (tb.getStatements()[0] instanceof PsiIfStatement) {
      PsiIfStatement ifStmt = (PsiIfStatement) tb.getStatements()[0];
      checkVar = StringConcatHandling.getCheckVariable(ifStmt);

      Optional<PsiExpression> delim = StreamEx.of(ifStmt.getThenBranch(), ifStmt.getElseBranch())
        .nonNull().map(b -> b instanceof PsiBlockStatement ? ((PsiBlockStatement) b).getCodeBlock().getStatements() : new PsiStatement[] { b })
        .filter(cb -> cb.length == 1)
        .map(cb -> StringConcatHandling.getAppendParam(var.get(), cb[0], stringConcat))
        .nonNull().findAny();
      if (!delim.isPresent()) return null;

      boolean getValue = !TypeUtils.expressionHasTypeOrSubtype(delim.get(), StringConcatHandling.JAVA_LANG_CHARSEQUENCE);
      if (getValue) {
        builder.append(CommonClassNames.JAVA_LANG_STRING);
        builder.append(".valueOf(");
      }
      builder.append(delim.get().getText());
      if (getValue) {
        builder.append(")");
      }
    }
    builder.append("))");

    return replaceWithStringConcatenation(project, loopStatement, var.get(), checkVar, builder, stringConcat);
  }

}
