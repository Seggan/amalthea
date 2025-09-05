package io.github.seggan.amalthea.backend

import io.github.seggan.amalthea.frontend.CodeSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

data class OutputClass(val pkg: List<String>, val name: String) {

    val jvmName = (pkg + name).joinToString("/")

    fun write(root: Path, bytes: ByteArray) {
        val packagePath = pkg.fold(root) { acc, part -> acc.resolve(part) }
        packagePath.createDirectories()
        packagePath.resolve("$name.class").writeBytes(bytes)
    }

    companion object {
        fun fromSource(source: CodeSource, pkg: List<String>): OutputClass {
            val name = source.name.substringBeforeLast('.').replaceFirstChar(Char::uppercaseChar) + "Am"
            return OutputClass(pkg, name)
        }
    }
}
