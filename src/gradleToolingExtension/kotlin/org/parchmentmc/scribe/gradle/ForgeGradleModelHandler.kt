/*
 * Scribe
 * Copyright (C) 2022 ParchmentMC
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
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.parchmentmc.scribe.ParchmentMappings

object ForgeGradleModelHandler {
    fun build(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, resolverCtx: ProjectResolverContext) {
        val data = resolverCtx.getExtraProject(gradleModule, ForgeGradleModel::class.java) ?: return

        val gradleProjectPath = gradleModule.gradleProject.projectIdentifier.projectPath
        val suffix = if (gradleProjectPath.endsWith(':')) "" else ":"
        val taskName = gradleProjectPath + suffix + data.getExtractSrgTaskName()

        val modelData = ForgeGradleIntellijModel(ideModule.data, data.getMcVersion(), taskName, data.getExtractSrgTaskOutput(), data.getClientMappings())
        ideModule.createChild(ForgeGradleIntellijModel.KEY, modelData)
        ParchmentMappings.invalidateHints()

        for (child in ideModule.children) {
            val childData = child.data
            if (childData is GradleSourceSetData) {
                child.createChild(ForgeGradleIntellijModel.KEY, modelData.copy(ideModule = childData))
            }
        }
    }
}