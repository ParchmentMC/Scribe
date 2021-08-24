package me.sizableshrimp.intelliparchment.gradle

import groovy.transform.CompileStatic

@CompileStatic
class ForgeGradleModelImpl implements ForgeGradleModel, Serializable {
    final String mcVersion
    final String extractSrgTaskName
    final File extractSrgTaskOutput
    final File clientMappings

    ForgeGradleModelImpl(String mcVersion, String extractSrgTaskName, File extractSrgTaskOutput, File clientMappings) {
        this.mcVersion = mcVersion
        this.extractSrgTaskName = extractSrgTaskName
        this.extractSrgTaskOutput = extractSrgTaskOutput
        this.clientMappings = clientMappings
    }
}
