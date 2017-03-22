package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
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
    StreamApiMigrationInspection.InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(var, loopStatement);
    if (stringConcat) {
      // Unlike StringBuilder, String concats don't use in-place refactoring
      if (status == StreamApiMigrationInspection.InitializerUsageStatus.UNKNOWN) return null;

      // Get initializer constructor argument if present
      String sbArg = Optional.ofNullable(var.getInitializer()).map(PsiElement::getText).orElse(null);
      if (sbArg != null && !"\"\"".equals(sbArg)) {
        builder.insert(0, " + ");
        builder.insert(0, sbArg);
      }
    }

    // Replace variable declaration type and initializer
    if (var.getTypeElement() != null) {
      if (checkVar != null) {
        // TODO: Check other usages!
        checkVar.delete();
        // Refresh status after removing switch variable
        status = StreamApiMigrationInspection.getInitializerUsageStatus(var, loopStatement);
      }
      if (stringConcat) {
        PsiExpression initializer = var.getInitializer();
        return replaceInitializer(loopStatement, var, initializer, builder.toString(), status);
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

    PsiType type = TypeUtils.getType(CommonClassNames.JAVA_LANG_STRING, body);

    PsiVariable checkVar = null;

    StringBuilder builder = generateStream(new MapOp(tb.getLastOperation(), appended.get(), tb.getVariable(), type));
    builder.append(".collect(java.util.stream.Collectors.joining(");
    if (tb.getStatements()[0] instanceof PsiIfStatement) {
      PsiIfStatement ifStmt = (PsiIfStatement) tb.getStatements()[0];
      checkVar = StringConcatHandling.getFIVariable(ifStmt);

      Optional<PsiExpression> delim = StreamEx.of(ifStmt.getThenBranch(), ifStmt.getElseBranch())
        .nonNull().map(b -> ((PsiBlockStatement) b).getCodeBlock())
        .filter(cb -> cb.getStatements().length == 1)
        .map(cb -> StringConcatHandling.getAppendParam(var.get(), cb.getStatements()[0], stringConcat))
        .nonNull().filter(e -> TypeUtils.expressionHasTypeOrSubtype(e, CommonClassNames.JAVA_LANG_STRING))
        .findAny();
      if (!delim.isPresent()) return null;

      builder.append(delim.get().getText());
    }
    builder.append("))");

    return replaceWithStringConcatenation(project, loopStatement, var.get(), checkVar, builder, stringConcat);
  }

}
