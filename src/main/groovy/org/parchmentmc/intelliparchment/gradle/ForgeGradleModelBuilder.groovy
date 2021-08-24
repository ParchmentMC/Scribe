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

package org.parchmentmc.intelliparchment.gradle

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

        def mappingVersion = (mcExtension.mappingVersion as Provider)?.get() ?: mcExtension.mappingVersion
        if (!(mappingVersion instanceof String))
            return null
        def taskOutput = task.outputs.files.singleFile
        def mcVersion = getMCVersion(mappingVersion)
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
            if (mcVersion < "1.14.4")
                return null
            return project.configurations.detachedConfiguration(project.dependencies.create("net.minecraft:client:$mcVersion:mappings@txt")).resolve().iterator().next()
        } catch (Exception ignored) {
            return null
        }
    }
}
