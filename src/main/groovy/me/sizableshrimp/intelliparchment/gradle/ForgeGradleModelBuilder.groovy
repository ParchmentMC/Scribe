package me.sizableshrimp.intelliparchment.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

import java.util.regex.Pattern

class ForgeGradleModelBuilder implements ModelBuilderService {
    private static final Pattern MCP_CONFIG_VERSION = Pattern.compile("\\d{8}\\.\\d{6}") //Timestamp: YYYYMMDD.HHMMSS

    @Override
    boolean canBuild(String modelName) {
        return ForgeGradleModel.name == modelName
    }

    @Override
    ForgeGradleModel buildAll(String modelName, Project project) {
        def mcExtension = project?.extensions?.findByName("minecraft")
        def task = project.tasks.findByName("extractSrg")
        if (mcExtension == null || task == null)
            return null

        String mcVersion = getMCVersion((mcExtension.mappingVersion as Provider)?.get() ?: mcExtension.mappingVersion as String)
        def taskOutput = task.outputs.files.singleFile
        if (mcVersion == null || taskOutput == null)
            return null

        return new ForgeGradleModelImpl(mcVersion, task.name, taskOutput, getClientMappings(project, mcVersion))
    }

    @Override
    ErrorMessageBuilder getErrorMessageBuilder(Project project, Exception e) {
        return ErrorMessageBuilder.create(
                project, e, "Parchment ForgeGradle errors"
        ).withDescription("Unable to integrate Parchment with ForgeGradle")
    }

    private static String getMCVersion(String version) {
        int idx = version.lastIndexOf('-')
        if (idx != -1 && MCP_CONFIG_VERSION.matcher(version.substring(idx + 1)).matches()) {
            return version.substring(version.lastIndexOf('-', idx - 1) + 1, idx)
        }
        return version.substring(idx + 1)
    }

    private static File getClientMappings(Project project, String mcVersion) {
        try {
            return project.configurations.detachedConfiguration(project.dependencies.create("net.minecraft:client:$mcVersion:mappings@txt")).resolve().iterator().next()
        } catch (Exception ignored) {
            return null
        }
    }
}
