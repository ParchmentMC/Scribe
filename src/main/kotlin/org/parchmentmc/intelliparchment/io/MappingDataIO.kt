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

package org.parchmentmc.intelliparchment.io

import org.parchmentmc.feather.mapping.MappingDataContainer
import org.parchmentmc.feather.mapping.VersionedMDCDelegate
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer
import java.io.File
import java.io.IOException
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