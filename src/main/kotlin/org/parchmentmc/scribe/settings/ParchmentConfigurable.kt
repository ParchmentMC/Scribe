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

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UI
import org.parchmentmc.scribe.ParchmentMappings
import java.io.IOException
import javax.swing.JCheckBox

class ParchmentConfigurable : BoundConfigurable("Parchment Settings"), SearchableConfigurable {
    private lateinit var mappingsFolderField: TextFieldWithBrowseButton
    private lateinit var displayHintsCheckbox: JCheckBox
    private val settings = ParchmentSettings.instance

    override fun getDisplayName(): String = "Parchment Settings"

    override fun getId(): String = ID

    override fun createPanel(): DialogPanel = panel {
        // row {
        //     cell {
        //         label("These settings control how mappings are output to allow mapping for Pull Requests to Parchment from inside IntelliJ.", font = JBFont.label().deriveFont(14F))
        //     }
        // }
        row {
            cell {
                mappingsFolderField = com.intellij.ui.components.textFieldWithBrowseButton(
                    null,
                    "Choose Mappings Folder",
                    fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                )
                mappingsFolderField.textField.text = settings.mappingsFolder
                component(
                    UI.PanelFactory.panel(mappingsFolderField)
                        .withLabel("Parchment Mappings Folder:")
                        .withComment("<p>The folder to use when parsing and generating Parchment mappings. Must be in Enigma <code>.mapping</code> file format!</p>")
                        .createPanel()
                )
                mappingsFolderField.toolTipText = "The output folder to use when generating Parchment mappings."
            }
        }
        row {
            cell {
                displayHintsCheckbox = checkBox(
                    "Display Parchment Hints",
                    isSelected = settings.displayHints,
                    comment = "Determines whether Parchment hints like parameters and javadocs should be shown as parsed from the Mappings Folder."
                ).component
            }
        }
    }

    override fun reset() {
        mappingsFolderField.textField.text = settings.mappingsFolder
        displayHintsCheckbox.isSelected = settings.displayHints
    }

    override fun isModified(): Boolean {
        return isModified(mappingsFolderField.textField, settings.mappingsFolder) || isModified(displayHintsCheckbox, settings.displayHints)
    }

    @Suppress("UnstableApiUsage")
    override fun apply() {
        if (isModified)
            ParchmentMappings.invalidateHints()
        val mappingsFolderModified = isModified(mappingsFolderField.textField, settings.mappingsFolder)
        settings.mappingsFolder = mappingsFolderField.text
        if (mappingsFolderModified) {
            try {
                ParchmentMappings.resetMappingContainer()
            } catch (e: IOException) {
                Messages.showErrorDialog("The folder specified was invalid", "Invalid Parchment Mappings Folder")
            }
        }
        settings.displayHints = displayHintsCheckbox.isSelected
    }

    companion object {
        const val ID = "Settings.Parchment"
    }
}