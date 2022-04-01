/*
 * Scribe
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

package org.parchmentmc.scribe.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UI
import org.parchmentmc.scribe.ParchmentMappings
import java.io.IOException
import javax.swing.JCheckBox

class ParchmentProjectConfigurable(private val project: Project) : BoundConfigurable("Parchment Settings"), SearchableConfigurable {
    private lateinit var mappingsPathField: TextFieldWithBrowseButton
    private lateinit var displayHintsCheckbox: JCheckBox
    private lateinit var remapParametersCheckbox: JCheckBox
    private val settings = ParchmentProjectSettings.getInstance(project)
    private val mappings = ParchmentMappings.getInstance(project)

    override fun getId(): String = ID

    override fun createPanel(): DialogPanel = panel {
        // row {
        //     cell {
        //         label("These settings control how mappings are output to allow mapping for Pull Requests to Parchment from inside IntelliJ.", font = JBFont.label().deriveFont(14F))
        //     }
        // }
        row {
            cell {
                mappingsPathField = com.intellij.ui.components.textFieldWithBrowseButton(
                    project = project,
                    "Choose Mappings Path",
                    fileChooserDescriptor = FileChooserDescriptor(true, true, true, true, false, false)
                        .withFileFilter { it.isDirectory || it.name.endsWith(".json") || it.name.endsWith(".zip") }
                        .withDescription("Selected path may be a folder with enigma .mapping files, a ZIP archive with an enclosed parchment.json file, or a JSON file.")
                )
                mappingsPathField.textField.text = settings.mappingsPath
                component(
                    UI.PanelFactory.panel(mappingsPathField)
                        .withLabel("Parchment Mappings Path:")
                        .withComment("<p>The folder or file to use when parsing and generating Parchment mappings. Only a folder with enigma .mapping files allows remapping data.</p>")
                        .createPanel()
                )
            }
            row {
                button("Save as Default Path") {
                    ParchmentProjectSettings.getInstance(ProjectManager.getInstance().defaultProject).mappingsPath = settings.mappingsPath
                }.comment("<p>Saves the current mappings path as the default path to load when projects have no mappings path specified.</p>")
            }
        }
        row {
            cell {
                displayHintsCheckbox = checkBox(
                    "Display Parchment Hints",
                    isSelected = settings.displayHints,
                    comment = "Determines whether Parchment hints like parameters and javadocs should be shown as parsed from the Mappings Path."
                ).component
            }
        }
        row {
            cell {
                remapParametersCheckbox = checkBox(
                    "Remap Parameters",
                    isSelected = settings.remapParameters,
                    comment = "Determines whether Scribe should automatically remap parameters when inserting constructors and overrides."
                ).component
            }
        }
    }

    override fun reset() {
        mappingsPathField.textField.text = settings.mappingsPath
        displayHintsCheckbox.isSelected = settings.displayHints
        remapParametersCheckbox.isSelected = settings.remapParameters
    }

    override fun isModified(): Boolean {
        return isModified(mappingsPathField.textField, settings.mappingsPath)
                || isModified(displayHintsCheckbox, settings.displayHints)
                || isModified(remapParametersCheckbox, settings.remapParameters)
    }

    @Suppress("UnstableApiUsage")
    override fun apply() {
        if (isModified)
            ParchmentMappings.invalidateHints()
        val mappingsFolderModified = isModified(mappingsPathField.textField, settings.mappingsPath)
        settings.mappingsPath = mappingsPathField.text
        if (mappingsFolderModified) {
            try {
                mappings.resetMappingContainer()
            } catch (e: IOException) {
                Messages.showErrorDialog("The path specified was invalid: $e", "Invalid Parchment Mappings Path")
            }
        }
        settings.displayHints = displayHintsCheckbox.isSelected
        settings.remapParameters = remapParametersCheckbox.isSelected
    }

    companion object {
        const val ID = "Settings.Parchment"
    }
}