package me.sizableshrimp.intelliparchment.settings

import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import me.sizableshrimp.intelliparchment.ParchmentMappings

@State(name = "ParchmentSettings", storages = [Storage("parchment_settings.xml")])
class ParchmentSettings : PersistentStateComponent<ParchmentSettings.State> {
    data class State(
        var mappingsFolder: String = "",
        var displayHints: Boolean = true
    )

    private var state = State()

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }

    // Wrappers
    var mappingsFolder: String
        get() = state.mappingsFolder
        set(value) {
            state.mappingsFolder = value
        }

    @Suppress("UnstableApiUsage")
    var displayHints: Boolean
        get() = state.displayHints
        set(value) {
            state.displayHints = value
        }

    companion object {
        val instance: ParchmentSettings
            get() = ApplicationManager.getApplication().getService(ParchmentSettings::class.java)
    }
}
