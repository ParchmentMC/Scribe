# Scribe
Scribe is an IntelliJ IDEA plugin that integrates with [Parchment mappings](https://github.com/ParchmentMC/Parchment).
Supported features include:
* Loading mapping data from a Parchment data folder, export JSON file, or ZIP archive and displaying parameter hints
* Mapping parameters, adding to javadocs, and saving to disk
* Applying Parchment parameter data when overriding mapped methods and constructors

## Installation
Scribe can be found on the [Jetbrains Marketplace](https://plugins.jetbrains.com/plugin/17485-scribe).
Installation is the same as any other IntelliJ IDEA plugin.

## Setup
To use Scribe, you must point it to a supported file format to load mapping data from.
Supported formats include:
* `data` folder inside a Git clone of Parchment containing `.mapping` files
* Parchment ZIP export with an entry named `parchment.json` inside
* Parchment JSON export file

Only the `data` folder supports modifying mapping data!

To setup, go to `Settings > Tools > Parchment Mappings`, and provide the full path to one of the supported formats.
This can be configured on a per-project basis.
You may want to click `Save as Default Path` to save the mapping path as the default for any projects which do not specify one.
After applying the settings, parameter hints will appear in classes with Parchment data.
If you use a `data` folder, a `Parchment` group will appear in the context menu to support remapping parameters and changing javadoc information.