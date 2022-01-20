package org.parchmentmc.scribe.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.parchmentmc.scribe.ParchmentMappings
import org.parchmentmc.scribe.util.jvmIndex

class RemapMethodParametersAction : MappingAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return

        if (!EditorModificationUtil.checkModificationAllowed(editor) || !FileDocumentManager.getInstance().requestWriting(editor.document, project)) {
            return
        }

        val method = when (val element = e.getData(CommonDataKeys.PSI_ELEMENT)) {
            is PsiMethod -> element
            else -> PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: return
        }

        remapMethodParameters(method, project, editor)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)

        if (e.presentation.isEnabledAndVisible) {
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val project = e.project ?: return

            e.presentation.isEnabledAndVisible = PsiUtilBase.getPsiFileInEditor(editor, project) !is PsiCompiledElement
        }
    }

    companion object {
        fun remapMethodParameters(method: PsiMethod, project: Project = method.project, editor: Editor? = null) {
            val methodData = ParchmentMappings.getMethodData(method, searchSupers = true) ?: return
            val parameters = method.parameterList.parameters

            DumbService.getInstance(project).smartInvokeLater {
                CommandProcessor.getInstance().executeCommand(project, {
                    parameters.forEachIndexed { index, parameter ->
                        val paramName = methodData.getParameter(parameter.jvmIndex)?.name ?: return@forEachIndexed
                        (parameters.getOrNull(index) as? PsiVariable)?.let {
                            val processor = RenamePsiElementProcessor.forElement(it)
                            val substituted: PsiElement? = processor.substituteElementToRename(it, null)
                            if (substituted == null || !PsiElementRenameHandler.canRename(project, null, substituted))
                                return@let

                            val dialog = processor.createRenameDialog(project, substituted, null, null)

                            try {
                                dialog.setPreviewResults(false)
                                dialog.performRename(paramName)
                            } finally {
                                dialog.close(DialogWrapper.CANCEL_EXIT_CODE) // to avoid dialog leak
                            }
                        }
                    }
                }, "Remap Method Parameters", editor?.let { DocCommandGroupId.noneGroupId(it.document) }, editor?.document)
            }
        }
    }
}