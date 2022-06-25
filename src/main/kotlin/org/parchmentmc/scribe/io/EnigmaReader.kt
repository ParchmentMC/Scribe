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

import org.parchmentmc.feather.mapping.MappingDataBuilder
import java.io.BufferedReader
import java.io.IOException

// Helper package-only class, to separate reading
internal object EnigmaReader {
    private val WHITESPACE = Regex("\\s")

    @Throws(IOException::class)
    fun readFile(builder: MappingDataBuilder, reader: BufferedReader) {
        var classData: MappingDataBuilder.MutableClassData? = null
        var methodData: MappingDataBuilder.MutableMethodData? = null
        var javadoc: MappingDataBuilder.MutableHasJavadoc<*>? = null
        var prevClassIndent = -1
        val classNames = ArrayDeque<String>()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val tokens = line!!.trim { it <= ' ' }.split(WHITESPACE).toTypedArray()
            when (tokens[0].uppercase()) {
                EnigmaFormattedExplodedIO.CLASS -> {
                    val indent = countIndent(line!!)
                    var className = tokens[1]
                    var diff = prevClassIndent - indent
                    while (diff >= 0) {
                        classNames.removeFirst()
                        diff--
                    }
                    prevClassIndent = indent
                    if (classNames.isNotEmpty()) { // Within a class
                        className = classNames.first() + '$' + className
                    }
                    classNames.addFirst(className)
                    classData = builder.createClass(className)
                    javadoc = classData
                }
                EnigmaFormattedExplodedIO.FIELD -> {
                    if (classData == null) throw IOException("Unexpected field line without class parent")
                    javadoc = classData.createField(tokens[1], tokens[2])
                }
                EnigmaFormattedExplodedIO.METHOD -> {
                    if (classData == null) throw IOException("Unexpected method line without class parent")
                    methodData = classData.createMethod(tokens[1], tokens[2])
                    javadoc = methodData
                }
                EnigmaFormattedExplodedIO.PARAM -> {
                    if (methodData == null) throw IOException("Unexpected arg line without method parent")
                    javadoc = methodData.createParameter(tokens[1].toByte())
                        .setName(tokens[2])
                }
                EnigmaFormattedExplodedIO.COMMENT -> {
                    if (javadoc == null) throw IOException("Unexpected comment line without javadoc-holding parent")
                    val strings = tokens.toMutableList()
                    if (strings.isNotEmpty()) {
                        strings.removeAt(0)
                    }
                    javadoc.addJavadoc(java.lang.String.join(" ", strings))
                }
            }
        }
    }

    private fun countIndent(line: String): Int {
        var indent = 0
        while (line[indent] == '\t') {
            indent++
        }
        return indent
    }
}