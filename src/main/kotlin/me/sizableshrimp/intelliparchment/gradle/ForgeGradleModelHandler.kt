package me.sizableshrimp.intelliparchment.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

object ForgeGradleModelHandler {
    fun build(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, resolverCtx: ProjectResolverContext) {
        val data = resolverCtx.getExtraProject(gradleModule, ForgeGradleModel::class.java) ?: return

        val gradleProjectPath = gradleModule.gradleProject.projectIdentifier.projectPath
        val suffix = if (gradleProjectPath.endsWith(':')) "" else ":"
        val taskName = gradleProjectPath + suffix + data.getExtractSrgTaskName()

        val modelData = ForgeGradleIntellijModel(ideModule.data, data.getMcVersion(), taskName, data.getExtractSrgTaskOutput(), data.getClientMappings())
        ideModule.createChild(ForgeGradleIntellijModel.KEY, modelData)

        for (child in ideModule.children) {
            val childData = child.data
            if (childData is GradleSourceSetData) {
                child.createChild(ForgeGradleIntellijModel.KEY, modelData.copy(ideModule = childData))
            }
        }
    }
}