package me.sizableshrimp.intelliparchment

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.util.io.isDirectory
import com.intellij.util.text.nullize
import me.sizableshrimp.intelliparchment.io.EnigmaFormattedExplodedIO
import me.sizableshrimp.intelliparchment.settings.ParchmentSettings
import me.sizableshrimp.intelliparchment.util.qualifiedMemberReference
import org.parchmentmc.feather.mapping.MappingDataBuilder
import org.parchmentmc.feather.mapping.MappingDataContainer
import java.nio.file.Paths

object ParchmentMappings {
    private val settings = ParchmentSettings.instance
    var mappingContainer: MappingDataBuilder? = null
        private set

    init {
        resetMappingContainer()
    }

    fun getParameterMapping(parameter: PsiParameter) = getParameterData(parameter)?.name

    fun getParameterData(parameter: PsiParameter): MappingDataContainer.ParameterData? {
        val containingMethod = parameter.declarationScope as? PsiMethod ?: return null
        val methodData = getMethodData(containingMethod) ?: return null
        return methodData.parameters.toList().getOrNull(containingMethod.parameterList.getParameterIndex(parameter))
    }

    fun getMethodJavadoc(method: PsiMethod): String? {
        val methodData = getMethodData(method) ?: return null
        val methodJavadoc = methodData.javadoc.joinToString("\n")
        if (methodData.parameters.size != method.parameterList.parameters.size)
            return methodJavadoc
        val builder = StringBuilder(methodJavadoc)

        for ((index, param) in methodData.parameters.withIndex()) {
            if (param.javadoc != null) {
                builder.append("\n@param ${param.name ?: method.parameterList.getParameter(index)?.name ?: continue} ${param.javadoc}")
            }
        }

        return builder.toString()
    }

    fun getMethodData(parameter: PsiParameter): MappingDataContainer.MethodData? {
        return getMethodData(parameter.declarationScope as? PsiMethod ?: return null)
    }

    fun getMethodData(method: PsiMethod): MappingDataContainer.MethodData? {
        if (mappingContainer == null)
            return null
        val methodRef = method.qualifiedMemberReference
        val containingClass = methodRef.owner?.replace('.', '/') ?: return null
        val methodDesc = methodRef.descriptor ?: return null

        return mappingContainer?.getClass(containingClass)?.getMethod(methodRef.name, methodDesc)
    }

    fun resetMappingContainer() {
        val folder = settings.mappingsFolder.nullize(nullizeSpaces = true)?.let(Paths::get)

        mappingContainer = if (folder == null || !folder.isDirectory()) null else EnigmaFormattedExplodedIO.INSTANCE.read(folder, true) as MappingDataBuilder
    }
}
