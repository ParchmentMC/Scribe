package me.sizableshrimp.intelliparchment.settings

import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UI
import me.sizableshrimp.intelliparchment.ParchmentMappings
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
            InlayHintsPassFactory.forceHintsUpdateOnNextPass()
        val mappingsFolderModified = isModified(mappingsFolderField.textField, settings.mappingsFolder)
        settings.mappingsFolder = mappingsFolderField.text
        if (mappingsFolderModified)
            ParchmentMappings.resetMappingContainer()
        settings.displayHints = displayHintsCheckbox.isSelected
    }

    companion object {
        const val ID = "Settings.Parchment"
    }
}