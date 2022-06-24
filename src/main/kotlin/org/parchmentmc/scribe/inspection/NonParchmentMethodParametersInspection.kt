package org.parchmentmc.scribe.inspection

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiVariable
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.util.SlowOperations
import com.siyeh.ig.BaseInspection
import com.siyeh.ig.BaseInspectionVisitor
import com.siyeh.ig.InspectionGadgetsFix
import org.parchmentmc.feather.mapping.MappingDataContainer
import org.parchmentmc.scribe.ParchmentMappings
import org.parchmentmc.scribe.util.jvmIndex

class NonParchmentMethodParametersInspection : BaseInspection() {
    override fun buildVisitor(): BaseInspectionVisitor {
        return object : BaseInspectionVisitor() {
            override fun visitMethod(method: PsiMethod?) {
                if (method == null)
                    return

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
                remapMethodParameters(descriptor.psiElement as? PsiMethod ?: return)
            }
        }

        fun isMismatched(methodData: MappingDataContainer.MethodData?, parameterList: PsiParameterList): Boolean = parameterList.parameters.any {
            methodData != null && methodData.getParameter(it.jvmIndex)?.name != it.name
        }

        fun remapMethodParameters(method: PsiMethod, project: Project = method.project, editor: Editor? = null) {
            val methodData = ParchmentMappings.getInstance(project).getMethodData(method, searchSupers = true) ?: return
            val parameters = method.parameterList.parameters

            DumbService.getInstance(project).smartInvokeLater {
                CommandProcessor.getInstance().executeCommand(project, {
                    SlowOperations.allowSlowOperations<Throwable> {
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
                    }
                }, "Remap Method Parameters", editor?.let { DocCommandGroupId.noneGroupId(it.document) }, editor?.document)
            }
        }
    }
}