package org.parchmentmc.scribe.gradle

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import org.gradle.plugins.ide.idea.model.Module

class ForgeGradleDataService : AbstractProjectDataService<ForgeGradleIntellijModel, Module>() {
    override fun getTargetDataKey(): Key<ForgeGradleIntellijModel> = ForgeGradleIntellijModel.KEY
}
