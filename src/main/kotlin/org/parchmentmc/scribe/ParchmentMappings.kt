/*
 * Scribe
 * Copyright (C) 2023 ParchmentMC
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

package org.parchmentmc.scribe

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterListOwner
import com.intellij.util.io.isDirectory
import com.intellij.util.text.nullize
import net.minecraftforge.srgutils.IMappingBuilder
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.srgutils.MinecraftVersion
import org.parchmentmc.feather.mapping.MappingDataBuilder
import org.parchmentmc.feather.mapping.MappingDataContainer
import org.parchmentmc.feather.mapping.VersionedMDCDelegate
import org.parchmentmc.scribe.gradle.ForgeGradleIntellijModel
import org.parchmentmc.scribe.io.ArchiveMappingDataIO
import org.parchmentmc.scribe.io.EnigmaFormattedExplodedIO
import org.parchmentmc.scribe.io.JsonMappingDataIO
import org.parchmentmc.scribe.settings.ParchmentProjectSettings
import org.parchmentmc.scribe.util.MemberReference
import org.parchmentmc.scribe.util.findAllSuperConstructors
import org.parchmentmc.scribe.util.findGradleModule
import org.parchmentmc.scribe.util.fullQualifiedName
import org.parchmentmc.scribe.util.getParameterByJvmIndex
import org.parchmentmc.scribe.util.jvmIndex
import org.parchmentmc.scribe.util.qualifiedMemberReference
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.extension

@Service
class ParchmentMappings(project: Project) {
    private val classMapCache: Cache<DataNode<ModuleData>, IMappingFile> = CacheBuilder.newBuilder()
        .weakKeys()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()
    private val settings = ParchmentProjectSettings.getInstance(project)
    var mappingContainer: VersionedMDCDelegate<*>? = null
        private set
    var modified: Boolean = false

    // Wrapper
    private val mappingBuilder
        get() = mappingContainer?.delegate as? MappingDataBuilder

    /**
     * `true` if the mapping container is not null and a mutable builder which supports modification, `false` otherwise
     */
    val mappingsMutable: Boolean
        get() = mappingBuilder != null

    init {
        try {
            resetMappingContainer()
        } catch (e: IOException) {
            // Swallow
        }
    }

    fun getMappingsPathAsPath() = settings.mappingsPath.nullize(nullizeSpaces = true)?.let(Paths::get)

    fun getParameterMapping(parameter: PsiParameter, create: Boolean = false, searchSupers: Boolean = false) = getParameterData(parameter, create, searchSupers)?.name

    fun getOrCreateParameterData(parameter: PsiParameter) = getParameterData(parameter, create = true) as? MappingDataBuilder.MutableParameterData

    fun getParameterData(parameter: PsiParameter, create: Boolean = false, searchSupers: Boolean = false): MappingDataContainer.ParameterData? {
        if (mappingContainer == null)
            return null
        val methodData = getMethodData(parameter.declarationScope as? PsiParameterListOwner ?: return null, create = create, searchSupers = searchSupers) ?: return null

        return if (create) (methodData as? MappingDataBuilder.MutableMethodData)?.getOrCreateParameter(parameter.jvmIndex) else methodData.getParameter(parameter.jvmIndex)
    }

    fun getMethodJavadoc(method: PsiMethod): String? {
        val methodData = getMethodData(method) ?: return null
        val methodJavadoc = methodData.javadoc.joinToString("\n")
        if (methodData.parameters.size != method.parameterList.parameters.size)
            return methodJavadoc
        val builder = StringBuilder(methodJavadoc)

        for (paramData in methodData.parameters) {
            if (paramData.javadoc != null) {
                builder.append("\n@param ${paramData.name ?: method.getParameterByJvmIndex(paramData.index)?.name ?: continue} ${paramData.javadoc}")
            }
        }

        return builder.toString()
    }

    private fun getMethodData(parameterListOwner: PsiParameterListOwner, create: Boolean = false, searchSupers: Boolean = false): MappingDataContainer.MethodData? {
        if (mappingContainer == null)
            return null

        return when (parameterListOwner) {
            is PsiMethod -> getMethodData(parameterListOwner, create = create, searchSupers = searchSupers)
            is PsiLambdaExpression -> getMethodData(parameterListOwner, create = create)
            else -> null
        }
    }

    fun getMethodData(lambda: PsiLambdaExpression, create: Boolean = false): MappingDataContainer.MethodData? {
        if (mappingContainer == null)
            return null
        val memberRef = lambda.qualifiedMemberReference ?: return null

        return getClassMemberData(memberRef, lambda, create, IMappingFile.IClass::remapMethod) { classData, methodName, methodDesc ->
            if (create) (classData as? MappingDataBuilder.MutableClassData)?.getOrCreateMethod(methodName, methodDesc) else classData.getMethod(methodName, methodDesc)
        }
    }

    fun getOrCreateMethodData(method: PsiMethod) = getMethodData(method, create = true) as? MappingDataBuilder.MutableMethodData

    fun getMethodData(method: PsiMethod, create: Boolean = false, searchSupers: Boolean = false): MappingDataContainer.MethodData? {
        if (mappingContainer == null)
            return null
        val memberRef = method.qualifiedMemberReference
        val methodData = getClassMemberData(memberRef, method, create, IMappingFile.IClass::remapMethod) { classData, methodName, methodDesc ->
            if (create) (classData as? MappingDataBuilder.MutableClassData)?.getOrCreateMethod(methodName, methodDesc) else classData.getMethod(methodName, methodDesc)
        }

        if (methodData == null && !create && searchSupers) {
            if (method.isConstructor) {
                method.findAllSuperConstructors().forEach { superConstructor ->
                    // Return if not null
                    getMethodData(superConstructor, create = false, searchSupers = false)?.let { return it }
                }
            } else {
                method.findSuperMethods().forEach { superMethod ->
                    // Return if not null
                    getMethodData(superMethod, create = false, searchSupers = false)?.let { return it }
                }
            }
        }

        return methodData
    }

    fun getOrCreateFieldData(field: PsiField) = getFieldData(field, create = true) as? MappingDataBuilder.MutableFieldData

    fun getFieldData(field: PsiField, create: Boolean = false): MappingDataContainer.FieldData? {
        if (mappingContainer == null)
            return null
        return getClassMemberData(field.qualifiedMemberReference, field, create, { srgClass, name, _ -> srgClass.remapField(name) }) { classData, fieldName, fieldDesc ->
            if (create) (classData as? MappingDataBuilder.MutableClassData)?.getOrCreateField(fieldName, fieldDesc) else classData.getField(fieldName)
        }
    }

    fun getOrCreateClassData(clazz: PsiClass) = getClassData(clazz, create = true) as? MappingDataBuilder.MutableClassData

    fun getClassData(clazz: PsiClass, create: Boolean = false): MappingDataContainer.ClassData? {
        val className = clazz.fullQualifiedName?.replace('.', '/') ?: return null
        val srgToMoj = getSrgToMoj(clazz)
        val remappedName = srgToMoj?.remapClass(className) ?: className

        return if (create) mappingBuilder?.getOrCreateClass(remappedName) else mappingContainer?.getClass(remappedName)
    }

    private fun <T> getClassMemberData(
        memberRef: MemberReference, element: PsiElement, create: Boolean, nameRemapper: (IMappingFile.IClass, String, String) -> String?,
        applier: (MappingDataContainer.ClassData, String, String) -> T
    ): T? {
        if (mappingContainer == null)
            return null
        val containingClass = memberRef.owner?.replace('.', '/') ?: return null
        val memberDesc = memberRef.descriptor ?: return null

        val srgToMoj = getSrgToMoj(element)
        val srgClass = srgToMoj?.getClass(containingClass)
        val remappedClass = srgClass?.mapped ?: containingClass
        val remappedDesc = srgToMoj?.remapDescriptor(memberDesc) ?: memberDesc
        val remappedName = srgClass?.let { nameRemapper(it, memberRef.name, remappedDesc) } ?: memberRef.name

        val classData = (if (create) mappingBuilder?.getOrCreateClass(remappedClass) else mappingContainer?.getClass(remappedClass)) ?: return null
        return applier(classData, remappedName, remappedDesc)
    }

    fun resetMappingContainer() {
        modified = false
        val path = getMappingsPathAsPath()

        try {
            mappingContainer = if (path == null) {
                null
            } else if (path.isDirectory()) {
                EnigmaFormattedExplodedIO.INSTANCE.read(path, true)
            } else if (path.extension == "json") {
                JsonMappingDataIO.INSTANCE.read(path, false)
            } else if (path.extension == "zip") {
                ArchiveMappingDataIO.INSTANCE.read(path, false)
            } else {
                null
            }
        } catch (e: Exception) {
            mappingContainer = null
            settings.mappingsPath = ""
            throw e
        }
    }

    private fun getSrgToMoj(element: PsiElement) = try {
        (element.containingFile as? PsiJavaFile)?.findGradleModule()?.let { gradleModule ->
            classMapCache.get(gradleModule) {
                val fgModel = gradleModule.children.find { it.key == ForgeGradleIntellijModel.KEY }?.data as? ForgeGradleIntellijModel
                fgModel?.takeIf { it.clientMappings == null || isOfficialVersion(it.mcVersion) }?.let {
                    return@get IMappingBuilder.create().build().getMap("left", "right") // Return empty data
                }
                if (fgModel == null)
                    throw Exception() // Throw an exception that is immediately swallowed, we want to keep checking the cache

                IMappingFile.load(fgModel.clientMappings).chain(IMappingFile.load(fgModel.extractSrgTaskOutput)).reverse()
            }
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private val v1_17 = MinecraftVersion.from("1.17")

        private fun isOfficialVersion(mcVersion: String) = try {
            v1_17 <= MinecraftVersion.from(mcVersion)
        } catch (e: Exception) {
            false
        }

        fun getInstance(project: Project): ParchmentMappings = project.getService(ParchmentMappings::class.java)

        fun invalidateHints() = @Suppress("UnstableApiUsage") InlayHintsPassFactory.forceHintsUpdateOnNextPass()
    }
}
