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

package org.parchmentmc.scribe.io

import com.google.common.base.CharMatcher
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okio.buffer
import okio.sink
import okio.source
import org.parchmentmc.feather.mapping.ImmutableMappingDataContainer
import org.parchmentmc.feather.mapping.ImmutableMappingDataContainer.ImmutableClassData
import org.parchmentmc.feather.mapping.MappingDataBuilder
import org.parchmentmc.feather.mapping.MappingDataContainer.ClassData
import org.parchmentmc.feather.mapping.MappingDataContainer.PackageData
import org.parchmentmc.feather.mapping.VersionedMDCDelegate
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer
import org.parchmentmc.feather.util.SimpleVersion
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Objects
import java.util.SortedMap
import java.util.WeakHashMap
import java.util.function.Function
import java.util.stream.Collectors

private val internalHolderChildClassMap = WeakHashMap<ClassData, SortedMap<String, ClassData>>()

val ClassData.childClassMap: SortedMap<String, ClassData>
    get() = internalHolderChildClassMap.computeIfAbsent(this) { sortedMapOf(EnigmaFormattedExplodedIO.CLASS_NAME_LENGTH_THEN_LEXICOGRAPHICALLY) }

class EnigmaFormattedExplodedIO(private val moshi: Moshi, private val jsonIndent: String, private val extension: String) : MappingDataIO {
    @Throws(IOException::class)
    override fun write(data: VersionedMappingDataContainer, output: Path) {
        val existingFiles = output.takeIf(Files::exists)?.let {
            Files.walk(it).use { s -> s.filter(Files::isRegularFile).map(Path::toAbsolutePath).collect(Collectors.toSet()) }
        } ?: mutableSetOf()
        Files.createDirectories(output)

        val infoJson = output.resolve("info.json").toAbsolutePath()
        existingFiles.remove(infoJson)
        infoJson.sink().buffer().use { sink ->
            val info = DataInfo()
            info.version = data.formatVersion
            moshi.adapter(DataInfo::class.java).indent(jsonIndent).toJson(sink, info)
        }

        val packageJson = output.resolve("packages.json")
        existingFiles.remove(packageJson)
        packageJson.sink().buffer().use { sink -> moshi.adapter<Any>(PACKAGE_COLLECTION_TYPE).indent(jsonIndent).toJson(sink, data.packages) }

        val classMap = mutableMapOf<String, ClassData>()
        val classGenerator: (String) -> ClassData = { classname -> classMap.computeIfAbsent(classname) { data.getClass(classname) ?: emptyClassData(classname) } }

        // Generate all the child class map data
        data.classes.flatMapTo(mutableSetOf()) { EnigmaWriter.expandClass(it.name) }.forEach { classname ->
            val upperClassname = classname.substringBeforeLast('$')
            val upperClassData = classGenerator(upperClassname) // Can't inline this as we need to always generate the data
            if (upperClassname != classname) {
                upperClassData.childClassMap[classname.substringAfterLast('$')] = classGenerator(classname)
            }
        }

        // Write out classes
        classMap.values.filter { it.name.indexOf('$') == -1 }.forEach { classData ->
            val mappingFile = output.resolve("${classData.name}.$extension").toAbsolutePath()
            mappingFile.parent?.let(Files::createDirectories)
            existingFiles.remove(mappingFile)

            val currentData = if (Files.exists(mappingFile)) String(Files.readAllBytes(mappingFile)) else ""
            val newline = if (currentData.contains('\r')) "\r\n" else "\n"
            val builder = StringBuilder()
            EnigmaWriter.writeClass(builder, newline, 0, classData.name, classData)
            writeChildMap(builder, newline, classData)
            val newData = builder.toString()
            if (currentData != newData)
                Files.write(mappingFile, newData.toByteArray())
        }

        // Delete any remaining files that we didn't write
        existingFiles.forEach(Files::deleteIfExists)
    }

    @Throws(IOException::class)
    override fun read(input: Path, mutable: Boolean): VersionedMDCDelegate<*> {
        var info: DataInfo
        input.resolve("info.json").source().buffer().use {
            info = moshi.adapter(DataInfo::class.java).fromJson(it) ?: throw IOException("info.json did not deserialize")
        }

        var packages: Collection<PackageData>
        input.resolve("packages.json").source().buffer().use {
            packages = moshi.adapter<Collection<PackageData>>(PACKAGE_COLLECTION_TYPE).fromJson(it) ?: throw IOException("packages.json did not deserialize")
        }

        val builder = MappingDataBuilder()
        packages.forEach {
            builder.getOrCreatePackage(it.name).addJavadoc(it.javadoc)
        }

        Files.walkFileTree(input, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Objects.requireNonNull(file)
                Objects.requireNonNull(attrs)
                // Skip files not ending with the extension
                if (!file.toString().endsWith(extension)) return FileVisitResult.CONTINUE
                Files.newBufferedReader(file).use { reader -> EnigmaReader.readFile(builder, reader) }
                return FileVisitResult.CONTINUE
            }
        })

        val container = if (mutable) builder else ImmutableMappingDataContainer(builder.packages, builder.classes)
        return VersionedMDCDelegate(info.version ?: throw IllegalArgumentException("info.json version was not set"), container)
    }

    data class DataInfo(var version: SimpleVersion? = null)

    companion object {
        val INSTANCE = EnigmaFormattedExplodedIO(MOSHI, "    ", "mapping")
        private val DOLLAR_SIGN: CharMatcher = CharMatcher.`is`('$')
        internal val CLASS_NAME_LENGTH_THEN_LEXICOGRAPHICALLY: Comparator<String> = Comparator
            .comparingInt { obj: String -> obj.length }
            .thenComparing(Function.identity())
        const val CLASS = "CLASS"
        const val FIELD = "FIELD"
        const val METHOD = "METHOD"
        const val PARAM = "ARG"
        const val COMMENT = "COMMENT"
        private val PACKAGE_COLLECTION_TYPE = Types.newParameterizedType(
            MutableCollection::class.java, Types.subtypeOf(
                PackageData::class.java
            )
        )

        private fun emptyClassData(classname: String) = ImmutableClassData(classname, emptyList(), emptyList(), emptyList())

        private fun writeChildMap(builder: StringBuilder, newline: String, classData: ClassData) {
            classData.childClassMap.values.forEach {
                EnigmaWriter.writeClass(builder, newline, DOLLAR_SIGN.countIn(it.name), EnigmaWriter.stripToMostInner(it.name), it)
                writeChildMap(builder, newline, it)
            }
        }
    }
}