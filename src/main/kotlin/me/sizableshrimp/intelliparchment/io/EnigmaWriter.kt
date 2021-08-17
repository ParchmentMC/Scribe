package me.sizableshrimp.intelliparchment.io

import kotlin.Throws
import java.io.IOException
import org.parchmentmc.feather.mapping.MappingDataContainer
import java.util.TreeSet
import java.io.Writer
import java.util.Comparator
import java.util.LinkedHashSet

// Helper package-only class, to separate writing
internal object EnigmaWriter {
    fun stripToOuter(className: String): String {
        val classSeparator = className.indexOf('$')
        return if (classSeparator >= 0) {
            className.substring(0, classSeparator)
        } else className
    }

    fun stripToMostInner(className: String): String {
        val classSeparator = className.lastIndexOf('$')
        return if (classSeparator >= 0) {
            className.substring(classSeparator + 1)
        } else className
    }

    fun expandClass(className: String): Set<String> {
        var mutClassName = className
        if (mutClassName.indexOf('$') == -1) return emptySet()
        val expandedClasses: MutableSet<String> = LinkedHashSet()
        var pkg = ""
        val packageSeparator = mutClassName.lastIndexOf('/')
        if (packageSeparator > -1) {
            pkg = mutClassName.substring(0, packageSeparator + 1) // Include the /
            mutClassName = mutClassName.substring(packageSeparator + 1)
        }
        var prev: String? = null
        for (classComponent in mutClassName.split("$").toTypedArray()) {
            prev = (prev?.plus('$') ?: "") + classComponent
            expandedClasses.add(pkg + prev)
        }
        return expandedClasses
    }

    @Throws(IOException::class)
    fun indent(writer: Writer, indent: Int): Writer {
        for (i in 0 until indent) {
            writer.append('\t')
        }
        return writer
    }

    @Throws(IOException::class)
    fun writeClass(writer: Writer, indent: Int, name: String?, data: MappingDataContainer.ClassData) {
        indent(writer, indent).append(EnigmaFormattedExplodedIO.CLASS).append(' ').append(name).append('\n')
        val memberIndent = indent + 1
        val javadocIndent = indent + 2
        for (javadoc in data.javadoc) {
            writeComment(writer, memberIndent, javadoc)
        }
        val fieldDataComparator = Comparator
            .comparing { s: MappingDataContainer.FieldData -> s.name + s.descriptor }
        val fields = TreeSet(fieldDataComparator)
        fields.addAll(data.fields)
        for (field in fields) {
            indent(writer, memberIndent).append(EnigmaFormattedExplodedIO.FIELD).append(' ')
                .append(field.name).append(' ').append(field.descriptor).append('\n')
            for (javadoc in field.javadoc) {
                writeComment(writer, javadocIndent, javadoc)
            }
        }
        for (method in data.methods) {
            indent(writer, memberIndent).append(EnigmaFormattedExplodedIO.METHOD).append(' ')
                .append(method.name).append(' ').append(method.descriptor).append('\n')
            for (javadoc in method.javadoc) {
                writeComment(writer, javadocIndent, javadoc)
            }
            val paramIndent = memberIndent + 1
            for (param in method.parameters) {
                if (param.name == null) continue  // Skip non-named parameters
                indent(writer, paramIndent).append(EnigmaFormattedExplodedIO.PARAM).append(' ')
                    .append(param.index.toString()).append(' ').append(param.name)
                    .append('\n')
                param.javadoc?.let{
                    writeComment(writer, paramIndent + 1, it)
                }
            }
        }
    }

    @Throws(IOException::class)
    fun writeComment(writer: Writer, indent: Int, comment: String) {
        indent(writer, indent).append(EnigmaFormattedExplodedIO.COMMENT)
        if (comment.isNotEmpty()) {
            writer.append(' ').append(comment)
        }
        writer.append('\n')
    }
}