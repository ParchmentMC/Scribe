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

package org.parchmentmc.scribe.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

@State(name = "ParchmentProjectSettings", storages = [Storage(value = "parchment_settings.xml", roamingType = RoamingType.DISABLED)])
class ParchmentProjectSettings : PersistentStateComponent<ParchmentProjectSettings.State> {
    data class State(
        var mappingsPath: String = "",
        var displayHints: Boolean = true,
        var remapParameters: Boolean = true
    )

    private var state = State()

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }

    // Wrappers
    var mappingsPath: String
        get() {
            if (state.mappingsPath.isEmpty()) {
                return getInstance(ProjectManager.getInstance().defaultProject).state.mappingsPath
            }
            return state.mappingsPath
        }
        set(value) {
            state.mappingsPath = value
        }

    var displayHints: Boolean
        get() = state.displayHints
        set(value) {
            state.displayHints = value
        }

    var remapParameters: Boolean
        get() = state.remapParameters
        set(value) {
            state.remapParameters = value
        }

    companion object {
        fun getInstance(project: Project): ParchmentProjectSettings = project.getService(ParchmentProjectSettings::class.java)
    }
}
