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

package org.parchmentmc.scribe.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.util.containers.nullize
import net.minecraftforge.srgutils.MinecraftVersion
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.parchmentmc.scribe.util.runGradleTask
import java.nio.file.Paths

class ForgeGradleProjectResolverExtension : AbstractProjectResolverExtension() {
    override fun getExtraProjectModelClasses() = setOf(ForgeGradleModel::class.java)

    override fun getToolingExtensionsClasses() = extraProjectModelClasses

    @Suppress("UnstableApiUsage")
    override fun resolveFinished(projectDataNode: DataNode<ProjectData>) {
        val project = resolverCtx.externalSystemTaskId.findProject() ?: return
        val allTaskNames = findAllTaskNames(projectDataNode).nullize()?.distinct() ?: return

        val projectDirPath = Paths.get(projectDataNode.data.linkedExternalProjectPath)
        runGradleTask(project, projectDirPath) { settings ->
            settings.taskNames = allTaskNames
        }

        super.resolveFinished(projectDataNode)
    }

    private fun findAllTaskNames(node: DataNode<*>): List<String> {
        fun findAllTaskNames(node: DataNode<*>, taskNames: MutableList<String>) {
            val data = node.data
            if (data is ForgeGradleIntellijModel && MinecraftVersion.from(data.mcVersion) < MC_1_17) {
                taskNames += data.extractSrgTaskName
            }
            for (child in node.children) {
                findAllTaskNames(child, taskNames)
            }
        }

        val res = arrayListOf<String>()
        findAllTaskNames(node, res)
        return res
    }

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        ForgeGradleModelHandler.build(gradleModule, ideModule, resolverCtx)

        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    companion object {
        val MC_1_17 = MinecraftVersion.from("1.17")
    }
}