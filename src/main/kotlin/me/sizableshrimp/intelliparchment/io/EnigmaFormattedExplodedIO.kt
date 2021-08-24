/*
 * IntelliParchment
 * Copyright (C) 2021 SizableShrimp
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

package me.sizableshrimp.intelliparchment.io

import com.google.common.base.CharMatcher
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.buffer
import okio.sink
import okio.source
import org.parchmentmc.feather.io.moshi.LinkedHashSetMoshiAdapter
import org.parchmentmc.feather.io.moshi.MDCMoshiAdapter
import org.parchmentmc.feather.io.moshi.MetadataMoshiAdapter
import org.parchmentmc.feather.io.moshi.OffsetDateTimeAdapter
import org.parchmentmc.feather.io.moshi.SimpleVersionAdapter
import org.parchmentmc.feather.mapping.ImmutableMappingDataContainer
import org.parchmentmc.feather.mapping.ImmutableMappingDataContainer.ImmutableClassData
import org.parchmentmc.feather.mapping.MappingDataBuilder
import org.parchmentmc.feather.mapping.MappingDataContainer
import org.parchmentmc.feather.mapping.MappingDataContainer.PackageData
import org.parchmentmc.feather.mapping.VersionedMDCDelegate
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer
import org.parchmentmc.feather.util.SimpleVersion
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Objects
import java.util.TreeSet
import java.util.function.Function
import java.util.stream.Collectors

class EnigmaFormattedExplodedIO(private val moshi: Moshi, private val jsonIndent: String, private val extension: String) : MappingDataIO {
    @Throws(IOException::class)
    override fun write(data: VersionedMappingDataContainer, output: Path) {
        if (Files.exists(output)) {
            // noinspection ResultOfMethodCallIgnored
            Files.walk(output)
                .sorted(Comparator.reverseOrder())
                .map { obj: Path -> obj.toFile() }
                .forEach { obj: File -> obj.delete() }
        }
        Files.createDirectories(output)
        output.resolve("info.json").sink().buffer().use { sink ->
            val info = DataInfo()
            info.version = data.formatVersion
            moshi.adapter(DataInfo::class.java).indent(jsonIndent).toJson(sink, info)
        }
        output.resolve("packages.json").sink().buffer().use { sink -> moshi.adapter<Any>(PACKAGE_COLLECTION_TYPE).indent(jsonIndent).toJson(sink, data.packages) }

        // Group classes by their outermost classes (via `$` matching)
        val outerClassesToClasses = data.classes.stream()
            .map { obj: MappingDataContainer.ClassData -> obj.name }
            .sorted()
            .collect(
                Collectors.groupingBy(
                    EnigmaWriter::stripToOuter,
                    Collectors.toCollection { TreeSet(::compareClassNames) }
                )
            )
        val visited: MutableSet<String?> = HashSet()

        // Write out classes
        for ((outerClass, classes) in outerClassesToClasses) {
            val mappingFile = output.resolve("$outerClass.$extension")
            if (mappingFile.parent != null) {
                Files.createDirectories(mappingFile.parent)
            }
            Files.newBufferedWriter(mappingFile).use { writer ->
                visited.add(outerClass)
                var outerClassData = data.getClass(outerClass)
                // If the data for the outer class is not there, substitute an empty one
                if (outerClassData == null) outerClassData = ImmutableClassData(
                    outerClass, emptyList(), emptyList(), emptyList()
                )
                EnigmaWriter.writeClass(writer, 0, outerClass, outerClassData)
                for (clz in classes) {
                    if (clz!!.contentEquals(outerClass)) continue  // Skip the outer class
                    visited.add(clz)
                    for (component in EnigmaWriter.expandClass(clz)) {
                        if (visited.contains(component)) continue // Skip if it's already been visited
                        visited.add(component)
                        if (component.contentEquals(clz)) break // Skip if it's the class currently being written
                        EnigmaWriter.writeClass(
                            writer, DOLLAR_SIGN.countIn(component), EnigmaWriter.stripToMostInner(component),
                            ImmutableClassData(
                                component, emptyList(), emptyList(), emptyList()
                            )
                        )
                    }
                    var clzData = data.getClass(clz)
                    // If the data for the inner class is not there, substitute an empty one
                    if (clzData == null) clzData = ImmutableClassData(
                        clz, emptyList(), emptyList(), emptyList()
                    )
                    EnigmaWriter.writeClass(writer, DOLLAR_SIGN.countIn(clz), EnigmaWriter.stripToMostInner(clz), clzData)
                }
            }
        }
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

    internal class DataInfo {
        var version: SimpleVersion? = null
    }

    companion object {
        private val MOSHI = Moshi.Builder()
            .add(OffsetDateTimeAdapter())
            .add(MDCMoshiAdapter(true))
            .add(SimpleVersionAdapter())
            .add(LinkedHashSetMoshiAdapter.FACTORY)
            .add(MetadataMoshiAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val INSTANCE = EnigmaFormattedExplodedIO(MOSHI, "    ", "mapping")
        internal val DOLLAR_SIGN: CharMatcher = CharMatcher.`is`('$')
        private val CLASS_NAME_LENGTH_THEN_LEXICOGRAPHICALLY: Comparator<String> = Comparator
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

        fun compareClassNames(a: String, b: String): Int {
            val aComponents = a.split(DOLLAR_SIGN.toString()).toTypedArray()
            val bComponents = b.split(DOLLAR_SIGN.toString()).toTypedArray()
            var ret = 0
            val minimum = aComponents.size.coerceAtMost(bComponents.size)
            for (i in 0 until minimum) {
                val aComp = aComponents[i]
                val bComp = bComponents[i]
                ret = CLASS_NAME_LENGTH_THEN_LEXICOGRAPHICALLY.compare(aComp, bComp)
                if (ret != 0) break
            }
            if (ret == 0) {
                ret = CLASS_NAME_LENGTH_THEN_LEXICOGRAPHICALLY.compare(a, b)
            }
            return ret
        }
    }
}