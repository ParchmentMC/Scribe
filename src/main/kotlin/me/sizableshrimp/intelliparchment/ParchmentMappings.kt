/*
 * IntelliParchment
 * Copyright (C) 2021 SizableShrimp
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

package me.sizableshrimp.intelliparchment

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.util.io.isDirectory
import com.intellij.util.text.nullize
import me.sizableshrimp.intelliparchment.io.EnigmaFormattedExplodedIO
import me.sizableshrimp.intelliparchment.settings.ParchmentSettings
import me.sizableshrimp.intelliparchment.util.jvmIndex
import me.sizableshrimp.intelliparchment.util.qualifiedMemberReference
import org.parchmentmc.feather.mapping.MappingDataBuilder
import java.nio.file.Paths

object ParchmentMappings {
    private val settings = ParchmentSettings.instance
    var mappingContainer: MappingDataBuilder? = null
        private set
    var modified: Boolean = false

    // Wrapper
    val mappingFolderPath
        get() = settings.mappingsFolder.nullize(nullizeSpaces = true)?.let(Paths::get)

    init {
        resetMappingContainer()
    }

    fun getParameterMapping(parameter: PsiParameter, create: Boolean = false, searchSupers: Boolean = false) = getParameterData(parameter, create, searchSupers)?.name

    fun getParameterData(parameter: PsiParameter, create: Boolean = false, searchSupers: Boolean = false): MappingDataBuilder.MutableParameterData? {
        val containingMethod = parameter.declarationScope as? PsiMethod ?: return null
        val methodData = getMethodData(containingMethod, create = create, searchSupers = searchSupers) ?: return null
        return if (create) methodData.getOrCreateParameter(parameter.jvmIndex) else methodData.getParameter(parameter.jvmIndex)
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

    fun getMethodData(method: PsiMethod, create: Boolean = false, searchSupers: Boolean = false): MappingDataBuilder.MutableMethodData? {
        if (mappingContainer == null)
            return null
        val methodRef = method.qualifiedMemberReference
        val containingClass = methodRef.owner?.replace('.', '/') ?: return null
        val methodDesc = methodRef.descriptor ?: return null

        val classData = if (create) mappingContainer?.getOrCreateClass(containingClass) else mappingContainer?.getClass(containingClass)
        val methodData = if (create) classData?.getOrCreateMethod(methodRef.name, methodDesc) else classData?.getMethod(methodRef.name, methodDesc)

        if (methodData == null && !create && searchSupers) {
            method.findSuperMethods().forEach { superMethod ->
                // Return if not null
                getMethodData(superMethod, create, searchSupers)?.let { return it }
            }
        }

        return methodData
    }

    fun resetMappingContainer() {
        val folder = mappingFolderPath

        mappingContainer = if (folder == null || !folder.isDirectory()) null else EnigmaFormattedExplodedIO.INSTANCE.read(folder, true) as MappingDataBuilder
    }
}
