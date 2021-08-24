package me.sizableshrimp.intelliparchment.gradle

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import java.io.File

data class ForgeGradleIntellijModel(
    val ideModule: ModuleData,
    val mcVersion: String,
    val extractSrgTaskName: String,
    val extractSrgTaskOutput: File,
    val clientMappings: File?
) : AbstractExternalEntityData(ideModule.owner) {
    companion object {
        // Process after builtin services (e.g. dependency or module data)
        val KEY = Key.create(ForgeGradleIntellijModel::class.java, ProjectKeys.TASK.processingWeight + 1)
    }
}
