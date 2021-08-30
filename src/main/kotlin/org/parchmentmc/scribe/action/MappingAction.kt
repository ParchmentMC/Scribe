package org.parchmentmc.scribe.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.parchmentmc.scribe.ParchmentMappings

abstract class MappingAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = ParchmentMappings.mappingContainer != null
    }
}