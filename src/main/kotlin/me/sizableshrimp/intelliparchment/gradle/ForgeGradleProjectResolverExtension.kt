package me.sizableshrimp.intelliparchment.gradle

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.util.containers.nullize
import me.sizableshrimp.intelliparchment.util.runGradleTask
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
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
            if (data is ForgeGradleIntellijModel) {
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
}