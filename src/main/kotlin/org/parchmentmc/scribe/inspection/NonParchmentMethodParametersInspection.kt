/*
 * Scribe
 * Copyright (C) 2023 ParchmentMC
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

package org.parchmentmc.scribe.inspection

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiVariable
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.siyeh.ig.BaseInspection
import com.siyeh.ig.BaseInspectionVisitor
import com.siyeh.ig.InspectionGadgetsFix
import org.parchmentmc.feather.mapping.MappingDataContainer
import org.parchmentmc.scribe.ParchmentMappings
import org.parchmentmc.scribe.util.jvmIndex

class NonParchmentMethodParametersInspection : BaseInspection() {
    override fun buildVisitor(): BaseInspectionVisitor {
        return object : BaseInspectionVisitor() {
            override fun visitMethod(method: PsiMethod) {
                val mappings = ParchmentMappings.getInstance(method.project)
                if (isMismatched(mappings.getMethodData(method, searchSupers = true), method.parameterList)) {
                    registerError(method)
                }
            }
        }
    }

    override fun getStaticDescription(): String = "Method parameters are not using Parchment names"

    override fun buildErrorString(vararg infos: Any?): String = staticDescription

    override fun buildFix(vararg infos: Any?): InspectionGadgetsFix {
        return FIX
    }

    companion object {
        val FIX: InspectionGadgetsFix = object : InspectionGadgetsFix() {
            override fun getFamilyName(): String = "Remap method parameters to Parchment"

            override fun doFix(project: Project, descriptor: ProblemDescriptor) {
                remapMethodParameters(descriptor.psiElement as? PsiMethod ?: return, project)
            }
        }

        fun isMismatched(methodData: MappingDataContainer.MethodData?, parameterList: PsiParameterList): Boolean = parameterList.parameters.any {
            methodData != null && methodData.getParameter(it.jvmIndex)?.name != it.name
        }

        fun remapMethodParameters(method: PsiMethod, project: Project = method.project) {
            val methodData = ParchmentMappings.getInstance(project).getMethodData(method, searchSupers = true) ?: return
            val parameters = method.parameterList.parameters

            DumbService.getInstance(project).smartInvokeLater {
                parameters.forEachIndexed { index, parameter ->
                    val paramName = methodData.getParameter(parameter.jvmIndex)?.name ?: return@forEachIndexed
                    (parameters.getOrNull(index) as? PsiVariable)?.let {
                        val factory = RefactoringFactory.getInstance(project)
                        val renameRefactoring = factory.createRename(it, paramName, false, false)
                        renameRefactoring.run()
                    }
                }
            }
        }
    }
}