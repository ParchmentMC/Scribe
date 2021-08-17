package me.sizableshrimp.intelliparchment.hints

import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.impl.source.PsiMethodImpl
import com.intellij.refactoring.suggested.startOffset
import me.sizableshrimp.intelliparchment.ParchmentMappings
import me.sizableshrimp.intelliparchment.settings.ParchmentSettings
import java.util.Locale

@Suppress("UnstableApiUsage")
class ParchmentHintCollector(editor: Editor) : InlayHintsCollector {
    private val settings = ParchmentSettings.instance
    private val factory = PresentationFactory(editor as EditorImpl)

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        if (!settings.displayHints)
            return true

        when (element) {
            is PsiParameter -> {
                val mapped = ParchmentMappings.getParameterMapping(element) ?: return true
                if (element.name == mapped || element.name == "p${mapped.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }}") return true
                val hint = factory.roundWithBackgroundAndSmallInset(factory.text("$mapped:"))

                sink.addInlineElement(
                    element.nameIdentifier?.startOffset ?: return true,
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
