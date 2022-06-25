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

package org.parchmentmc.scribe.io

import org.parchmentmc.feather.mapping.VersionedMDCDelegate
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class ArchiveMappingDataIO(private val jsonMapper: JsonMappingDataIO) : MappingDataIO {
    @Throws(IOException::class)
    override fun write(data: VersionedMappingDataContainer, output: Path) {
        Files.deleteIfExists(output)
        output.parent?.let { Files.createDirectories(it) }

        FileSystems.newFileSystem(output, null).use { fs ->
            jsonMapper.write(data, fs.getPath("parchment.json"))
        }
    }

    @Throws(IOException::class)
    override fun read(input: Path, mutable: Boolean): VersionedMDCDelegate<*> = FileSystems.newFileSystem(input, null).use { fs ->
        jsonMapper.read(fs.getPath("parchment.json"), mutable)
    }

    companion object {
        val INSTANCE = ArchiveMappingDataIO(JsonMappingDataIO.INSTANCE)
    }
}