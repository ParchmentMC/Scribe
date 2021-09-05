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
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.ui.list.createTargetPopup
import com.intellij.util.text.nullize
import org.parchmentmc.scribe.ParchmentMappings
import org.parchmentmc.scribe.util.findAllSuperMethods
import java.util.Locale

class MapJavadocAction : MappingAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val element = e.getData(CommonDataKeys.PSI_ELEMENT) ?: return

        when (element) {
            is PsiParameter -> mapParameterJavadoc(element, e)
            is PsiMethod -> mapMethodJavadoc(element, e)
            is PsiField -> mapFieldJavadoc(element, e)
            is PsiClass -> mapClassJavadoc(element, e)
        }
    }

    private fun mapParameterJavadoc(parameter: PsiParameter, e: AnActionEvent) {
        val mapFun = fun(parameter: PsiParameter) {
            val currentJavadoc = ParchmentMappings.getParameterData(parameter)?.javadoc
            // Return early if they canceled (null), but then make null if it's empty or only has spaces
            val newJavadoc = (showInputDialog(e, "parameter", currentJavadoc) ?: return).nullize(nullizeSpaces = true)

            if (newJavadoc == currentJavadoc)
                return
            val parameterData = ParchmentMappings.getParameterData(parameter, create = true) ?: return

            parameterData.javadoc = newJavadoc
            ParchmentMappings.modified = true
            ParchmentMappings.invalidateHints()
        }

        MapParameterAction.mapParameter(e, parameter, mapFun)
    }

    private fun mapMethodJavadoc(method: PsiMethod, e: AnActionEvent) {
        val allSuperMethods = method.findAllSuperMethods()

        val mapFun = fun(method: PsiMethod) {
            val currentJavadoc = ParchmentMappings.getMethodJavadoc(method)
            // Return early if they canceled (null), but then make null if it's empty or only has spaces
            val newJavadoc = (showInputDialog(e, "method", currentJavadoc, multiline = true) ?: return).nullize(nullizeSpaces = true)

            if (newJavadoc == currentJavadoc)
                return
            val methodData = ParchmentMappings.getMethodData(method, create = true) ?: return

            methodData.clearJavadoc()
            newJavadoc?.split("\n")?.toMutableList()?.let { javadocs ->
                javadocs.removeIf {
                    // Remove any @param lines from the method javadoc but parse it into valid data
                    val isParam = it.startsWith("@param")
                    if (!isParam || it.indexOf(' ') == -1)
                        return@removeIf isParam

                    val paramName = it.substringAfter(' ').substringBefore(' ')
                    if (paramName != it) {
                        for (parameter in methodData.parameters) {
                            if (paramName == parameter.name)
                                parameter.javadoc = it.substringAfter(' ').substringAfter(' ')
                        }
                    }

                    return@removeIf isParam
                }
                methodData.addJavadoc(javadocs)
            }
            ParchmentMappings.modified = true
        }

        if (allSuperMethods.isNotEmpty()) {
            allSuperMethods.add(0, method)
            @Suppress("UnstableApiUsage")
            val popup = createTargetPopup("Choose method in inheritance structure to map", allSuperMethods, ::targetPresentation) { targetMethod -> mapFun(targetMethod) }
            popup.showInBestPositionFor(e.getData(CommonDataKeys.EDITOR) ?: return)
        } else {
            mapFun(method)
        }
    }

    private fun mapFieldJavadoc(field: PsiField, e: AnActionEvent) {
        val currentJavadoc = ParchmentMappings.getFieldData(field)?.javadoc?.joinToString("\n")
        // Return early if they canceled (null), but then make null if it's empty or only has spaces
        val newJavadoc = (showInputDialog(e, "field", currentJavadoc, multiline = true) ?: return).nullize(nullizeSpaces = true)

        if (newJavadoc == currentJavadoc)
            return
        val fieldData = ParchmentMappings.getFieldData(field, create = true) ?: return

        fieldData.clearJavadoc()
        fieldData.addJavadoc(newJavadoc?.split('\n') ?: listOf())
        ParchmentMappings.modified = true
        ParchmentMappings.invalidateHints()
    }

    private fun mapClassJavadoc(clazz: PsiClass, e: AnActionEvent) {
        val currentJavadoc = ParchmentMappings.getClassData(clazz)?.javadoc?.joinToString("\n")
        // Return early if they canceled (null), but then make null if it's empty or only has spaces
        val newJavadoc = (showInputDialog(e, "class", currentJavadoc, multiline = true) ?: return).nullize(nullizeSpaces = true)

        if (newJavadoc == currentJavadoc)
            return
        val classData = ParchmentMappings.getClassData(clazz, create = true) ?: return

        classData.clearJavadoc()
        classData.addJavadoc(newJavadoc?.split('\n') ?: listOf())
        ParchmentMappings.modified = true
        ParchmentMappings.invalidateHints()
    }

    private fun showInputDialog(e: AnActionEvent, type: String, currentJavadoc: String?, multiline: Boolean = false) = (if (multiline) Messages.showMultilineInputDialog(
        e.project, "Enter $type javadoc:", "Map ${type.capitalize()} Documentation",
        currentJavadoc, Messages.getQuestionIcon(), null
    ) else Messages.showInputDialog(
        e.project, "Enter $type javadoc:", "Map ${type.capitalize()} Documentation",
        Messages.getQuestionIcon(), currentJavadoc, null
    ))?.trim()
}

internal fun String.capitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }