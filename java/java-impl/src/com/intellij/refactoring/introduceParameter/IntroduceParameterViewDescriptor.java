/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import org.jetbrains.annotations.NotNull;

class IntroduceParameterViewDescriptor extends UsageViewDescriptorAdapter {

  private final PsiMethod myMethodToSearchFor;

  public IntroduceParameterViewDescriptor(PsiMethod methodToSearchFor
  ) {
    super();
    myMethodToSearchFor = methodToSearchFor;

  }

  @NotNull
  public PsiElement[] getElements() {
//    if(myMethodToReplaceIn.equals(myMethodToSearchFor)) {
//      return new PsiElement[] {myMethodToReplaceIn};
//    }
    return new PsiElement[]{myMethodToSearchFor};
  }


  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("introduce.parameter.elements.header");
  }
}
