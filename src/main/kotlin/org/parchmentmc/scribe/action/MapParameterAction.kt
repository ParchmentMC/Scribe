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

package org.parchmentmc.scribe.action

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.ui.list.createTargetPopup
import com.intellij.util.text.nullize
import org.parchmentmc.scribe.ParchmentMappings
import org.parchmentmc.scribe.util.findAllSuperConstructors
import org.parchmentmc.scribe.util.findAllSuperMethods

class MapParameterAction : MappingAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val parameter = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiParameter ?: return
        val mappings = ParchmentMappings.getInstance(project)

        val mapFun = fun(parameter: PsiParameter) {
            val currentName = mappings.getParameterMapping(parameter, searchSupers = true)
            // Return early if they canceled (null), but then make null if it's empty or only has spaces
            val mapped = (Messages.showInputDialog(
                e.project, "Enter a new parameter name:", "Map Parameter",
                Messages.getQuestionIcon(), currentName, inputValidator
            ) ?: return).nullize(nullizeSpaces = true)

            if (mapped == currentName)
                return
            val parameterData = mappings.getOrCreateParameterData(parameter) ?: return

            parameterData.name = mapped
            mappings.modified = true
            ParchmentMappings.invalidateHints()
        }

        mapParameter(e, parameter, mapFun)
    }

    companion object {
        private val parameterRegex = Regex("[a-z][a-zA-Z0-9]*")
        private val inputValidator = object : InputValidatorEx {
            override fun checkInput(inputString: String?) = isValid(inputString)

            override fun canClose(inputString: String?) = isValid(inputString)

            override fun getErrorText(inputString: String?): String? = if (isValid(inputString)) {
                null
            } else {
                "$inputString does not conform to the mapping standards. It must start with a lowercase letter and then only contain alphanumeric characters afterwards."
            }

            private fun isValid(inputString: String?) = inputString?.nullize(nullizeSpaces = true)?.let { parameterRegex matches it } ?: true
        }

        internal fun mapParameter(
            e: AnActionEvent,
            parameter: PsiParameter,
            mapFun: (PsiParameter) -> Unit
        ) {
            val declarationScope = parameter.declarationScope
            if (declarationScope is PsiMethod) {
                val allSuperMethods = if (declarationScope.isConstructor) {
                    declarationScope.findAllSuperConstructors()
                } else {
                    declarationScope.findAllSuperMethods()
                }
                if (allSuperMethods.isNotEmpty()) {
                    allSuperMethods.add(0, declarationScope)
                    @Suppress("UnstableApiUsage")
                    val popup = createTargetPopup("Choose method in inheritance structure to map", allSuperMethods, ::targetPresentation) { targetMethod ->
                        val newParam = targetMethod.parameterList.parameters.getOrNull(declarationScope.parameterList.getParameterIndex(parameter)) ?: return@createTargetPopup
                        mapFun(newParam)
                    }
                    popup.showInBestPositionFor(e.getData(CommonDataKeys.EDITOR) ?: return)
                } else {
                    mapFun(parameter)
                }
            } else {
                mapFun(parameter)
            }
        }
    }
}