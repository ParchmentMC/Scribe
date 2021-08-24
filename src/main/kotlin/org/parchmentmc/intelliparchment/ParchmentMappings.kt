/*
 * IntelliParchment
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

package org.parchmentmc.intelliparchment

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.util.io.isDirectory
import com.intellij.util.text.nullize
import net.minecraftforge.srgutils.IMappingBuilder
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.srgutils.MinecraftVersion
import org.parchmentmc.feather.mapping.MappingDataBuilder
import org.parchmentmc.feather.mapping.VersionedMDCDelegate
import org.parchmentmc.intelliparchment.gradle.ForgeGradleIntellijModel
import org.parchmentmc.intelliparchment.io.EnigmaFormattedExplodedIO
import org.parchmentmc.intelliparchment.settings.ParchmentSettings
import org.parchmentmc.intelliparchment.util.findGradleModule
import org.parchmentmc.intelliparchment.util.jvmIndex
import org.parchmentmc.intelliparchment.util.qualifiedMemberReference
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

object ParchmentMappings {
    private val v1_17 = MinecraftVersion.from("1.17")
    private val settings = ParchmentSettings.instance
    private val classMapCache: Cache<DataNode<ModuleData>, IMappingFile> = CacheBuilder.newBuilder()
        .weakKeys()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()
    var versionedMappingContainer: VersionedMDCDelegate<MappingDataBuilder>? = null
        private set
    var mappingContainer: MappingDataBuilder? = null
        private set
    var modified: Boolean = false

    // Wrapper
    val mappingFolderPath
        get() = settings.mappingsFolder.nullize(nullizeSpaces = true)?.let(Paths::get)

    init {
        try {
            resetMappingContainer()
        } catch (e: IOException) {
            // Swallow
        }
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

        val srgToMoj = getSrgToMoj(method)
        val remappedClass = srgToMoj?.remapClass(containingClass) ?: containingClass
        val remappedDesc = srgToMoj?.remapDescriptor(methodDesc) ?: methodDesc

        val classData = if (create) mappingContainer?.getOrCreateClass(remappedClass) else mappingContainer?.getClass(remappedClass)
        val methodData = if (create) classData?.getOrCreateMethod(methodRef.name, remappedDesc) else classData?.getMethod(methodRef.name, remappedDesc)

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

        try {
            versionedMappingContainer = if (folder == null || !folder.isDirectory()) {
                null
            } else {
                @Suppress("UNCHECKED_CAST")
                EnigmaFormattedExplodedIO.INSTANCE.read(folder, true) as VersionedMDCDelegate<MappingDataBuilder>
            }
            mappingContainer = versionedMappingContainer?.delegate
        } catch (e: Exception) {
            versionedMappingContainer = null
            mappingContainer = null
            settings.mappingsFolder = ""
            throw e
        }
    }

    private fun getSrgToMoj(method: PsiMethod) = try {
        (method.containingFile as? PsiJavaFile)?.findGradleModule()?.let { gradleModule ->
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

    private fun isOfficialVersion(mcVersion: String) = try {
        v1_17 <= MinecraftVersion.from(mcVersion)
    } catch (e: Exception) {
        false
    }
}
