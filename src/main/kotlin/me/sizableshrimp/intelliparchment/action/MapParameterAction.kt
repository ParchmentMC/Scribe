package me.sizableshrimp.intelliparchment.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.PsiParameterImpl
import com.intellij.util.text.nullize
import me.sizableshrimp.intelliparchment.ParchmentMappings

class MapParameterAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ParchmentMappings.mappingContainer != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val parameter = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiParameterImpl ?: return
        val containingMethod = parameter.declarationScope as? PsiMethod ?: return
        val currentName = ParchmentMappings.getParameterMapping(parameter)
        val mapped = Messages.showInputDialog(
            e.project, "Enter a new parameter name:", "Map Parameter",
            Messages.getQuestionIcon(), currentName, inputValidator
        ) ?: return
    }

    companion object {
        private val parameterRegex = Regex("[a-z][a-zA-Z0-9]*")
        private val inputValidator = object : InputValidatorEx {
            override fun checkInput(inputString: String?) = isValid(inputString)

            override fun canClose(inputString: String?) = isValid(inputString)

            override fun getErrorText(inputString: String?): String? = if (isValid(inputString)) {
                null
            } else if (inputString?.nullize(nullizeSpaces = true) == null) {
                "Parameter cannot be empty!"
            } else {
                "$inputString does not conform to the mapping standards. It must start with a lowercase letter and then only contain alphanumeric characters afterwards."
            }

            private fun isValid(inputString: String?) = inputString?.nullize(nullizeSpaces = true)?.let { parameterRegex matches it } ?: false
        }
    }
}