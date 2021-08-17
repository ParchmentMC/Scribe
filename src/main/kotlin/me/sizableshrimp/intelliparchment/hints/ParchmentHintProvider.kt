package me.sizableshrimp.intelliparchment.hints

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class ParchmentHintProvider : InlayHintsProvider<NoSettings> {
    override val name = "Parchment mapping suggestions"
    override val key = SettingsKey<NoSettings>("parchmentNoSettings")
    override val previewText: String? = null

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
        override fun createComponent(listener: ChangeListener): JComponent = JPanel()
    }

    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector {
        return ParchmentHintCollector(editor)
    }

    override fun isLanguageSupported(language: Language): Boolean {
        return language is JavaLanguage
    }
}
