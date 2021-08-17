package me.sizableshrimp.intelliparchment.io

import kotlin.Throws
import java.io.IOException
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer
import org.parchmentmc.feather.mapping.MappingDataContainer
import org.parchmentmc.feather.mapping.VersionedMDCDelegate
import java.io.File
import java.nio.file.Path

interface MappingDataIO {
    @Throws(IOException::class)
    fun write(data: VersionedMappingDataContainer, output: Path)

    @Throws(IOException::class)
    fun write(data: VersionedMappingDataContainer, output: File) {
        write(data, output.toPath())
    }

    @Throws(IOException::class)
    fun write(data: MappingDataContainer, output: Path) {
        write(VersionedMDCDelegate(VersionedMappingDataContainer.CURRENT_FORMAT, data), output)
    }

    @Throws(IOException::class)
    fun write(data: MappingDataContainer, output: File) {
        write(VersionedMDCDelegate(VersionedMappingDataContainer.CURRENT_FORMAT, data), output)
    }

    @Throws(IOException::class)
    fun read(input: Path, mutable: Boolean = false): MappingDataContainer

    @Throws(IOException::class)
    fun read(input: File, mutable: Boolean = false): MappingDataContainer {
        return read(input.toPath(), mutable)
    }
}