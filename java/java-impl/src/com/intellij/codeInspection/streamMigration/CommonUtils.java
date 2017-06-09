package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class CommonUtils {

  @NonNls public static final String BE_KULEUVEN_CS_DTAI_IMMUTABLE = "be.kuleuven.cs.dtai.Immutable";

  public static boolean isImmutable(@NotNull PsiType type) {
    if (ClassUtils.isImmutable(type)) return true;

    if (!(type instanceof PsiClassType)) return false;

    PsiClass returnClass = ((PsiClassType) type).resolve();
    return returnClass != null && AnnotationUtil.isAnnotated(returnClass, Collections.singletonList(BE_KULEUVEN_CS_DTAI_IMMUTABLE),
                                                             false, true);
  }

}
