/*
 * Scribe
 * Copyright (C) 2021 ParchmentMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.parchmentmc.scribe.overrideImplement

import com.intellij.codeInsight.MethodImplementor
import com.intellij.codeInsight.generation.GenerateMembersUtil
import com.intellij.codeInsight.generation.GenerationInfo
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.Consumer
import org.parchmentmc.scribe.ParchmentMappings
import org.parchmentmc.scribe.util.jvmIndex

class ParchmentMethodImplementor : MethodImplementor {
    override fun getMethodsToImplement(aClass: PsiClass?): Array<PsiMethod> = PsiMethod.EMPTY_ARRAY

    override fun createImplementationPrototypes(inClass: PsiClass, method: PsiMethod): Array<PsiMethod> {
        if (method.parameterList.parameters.isEmpty())
            return PsiMethod.EMPTY_ARRAY
        val methodData = ParchmentMappings.getMethodData(method, searchSupers = true) ?: return PsiMethod.EMPTY_ARRAY

        val containingClass = method.containingClass ?: return PsiMethod.EMPTY_ARRAY
        val substitutor = if (inClass.isInheritor(containingClass, true)) {
            TypeConversionUtil.getSuperClassSubstitutor(containingClass, inClass, PsiSubstitutor.EMPTY)
        } else {
            PsiSubstitutor.EMPTY
        }
        val overridenMethod = GenerateMembersUtil.substituteGenericMethod(method, substitutor, inClass)
        val copyClass = copyClass(inClass) ?: return PsiMethod.EMPTY_ARRAY
        val result = copyClass.add(overridenMethod) as PsiMethod

        method.parameterList.parameters.filterIsInstance<PsiParameter>().forEachIndexed { index, parameter ->
            val paramName = methodData.getParameter(parameter.jvmIndex)?.name ?: return@forEachIndexed
            (result.parameterList.parameters.getOrNull(index) as? PsiVariable)?.setName(paramName)
        }

        if (PsiUtil.isAnnotationMethod(result)) {
            val defaultValue = (result as PsiAnnotationMethod).defaultValue
            if (defaultValue != null) {
                var defaultKeyword: PsiElement? = defaultValue
                while (defaultKeyword !is PsiKeyword && defaultKeyword != null) {
                    defaultKeyword = defaultKeyword.prevSibling
                }
                if (defaultKeyword == null) defaultKeyword = defaultValue
                defaultValue.parent.deleteChildRange(defaultKeyword, defaultValue)
            }
        }

        return arrayOf(result)
    }

    private fun copyClass(aClass: PsiClass): PsiElement? {
        val marker = Any()
        PsiTreeUtil.mark(aClass, marker)
        val copy = aClass.containingFile.copy()
        return PsiTreeUtil.releaseMark(copy, marker)
    }

    override fun createGenerationInfo(method: PsiMethod, mergeIfExists: Boolean): GenerationInfo? = null

    override fun createDecorator(targetClass: PsiClass, baseMethod: PsiMethod, toCopyJavaDoc: Boolean, insertOverrideIfPossible: Boolean): Consumer<PsiMethod> =
        OverrideImplementUtil.createDefaultDecorator(targetClass, baseMethod, toCopyJavaDoc, insertOverrideIfPossible)
}