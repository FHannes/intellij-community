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
class ReplaceWithReduceFix extends MigrateToStreamFix {

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with reduce()";
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
        if (StringConcatHandling.isVariableReferencedAfter(checkVar, loopStatement)) {
          PsiExpression initializer = checkVar.getInitializer();
          if (initializer != null) {
            initializer.delete();
          }
        } else {
          checkVar.delete();
        }
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
    PsiAssignmentExpression stmt = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (stmt == null) return null;

    PsiVariable accumulator = ReduceHandling.getReductionAccumulator(stmt);
    if (accumulator == null) return null;

    restoreComments(loopStatement, loopStatement.getBody());

    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    StreamApiMigrationInspection.Operation op = tb.getLastOperation();
    StringBuilder builder = generateStream(op);
    builder.insert(0, " = ");
    builder.insert(0, accumulator.getName());
    builder.append(ReduceHandling.createReductionReplacement(stmt));

    return loopStatement.replace(elementFactory.createStatementFromText(builder.toString(), loopStatement));
  }

}
