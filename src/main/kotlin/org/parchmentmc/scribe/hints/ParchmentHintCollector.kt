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

package org.parchmentmc.scribe.hints

import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.psi.impl.source.PsiParameterImpl
import com.intellij.refactoring.suggested.startOffset
import org.parchmentmc.scribe.ParchmentMappings
import org.parchmentmc.scribe.settings.ParchmentSettings

@Suppress("UnstableApiUsage")
class ParchmentHintCollector(editor: Editor) : InlayHintsCollector {
    private val settings = ParchmentSettings.instance
    private val factory = PresentationFactory(editor as EditorImpl)

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (!settings.displayHints)
            return true

        when (element) {
            is PsiParameterImpl -> {
                val mapped = ParchmentMappings.getParameterMapping(element, searchSupers = true) ?: return true
                if (element.name == mapped/* || element.name == "p${mapped.capitalize()}"*/) return true
                val hint = factory.roundWithBackgroundAndSmallInset(factory.text("$mapped:"))

                sink.addInlineElement(
                    element.nameIdentifier.startOffset,
                    relatesToPrecedingText = false, presentation = hint, placeAtTheEndOfLine = false
                )
            }
            is PsiMethodImpl -> {
                // if (element.docComment != null)
                //     return true
                // val methodJavadoc = ParchmentMappings.getMethodJavadoc(element)?.split("\n")?.joinToString("\n* ", prefix = "/**\n", postfix = "\n*/") ?: return true
                // val psiDocComment = JavaPsiFacade.getElementFactory(element.getProject()).createDocCommentFromText(methodJavadoc)
                //
                // val elementCopy = ChangeUtil.copyToElement(psiDocComment)
                // val anchor = element.firstChild
                //
                // element.addBefore(psiDocComment, anchor)
            }
        }

        return true
    }
}
