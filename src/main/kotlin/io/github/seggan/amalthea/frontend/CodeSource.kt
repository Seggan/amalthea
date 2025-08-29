package io.github.seggan.amalthea.frontend

import java.lang.ref.SoftReference
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * Represents a place where source code can be retrieved from.
 *
 * @property name The name of the source.
 * @property textGetter A function that retrieves the source code from the name.
 */
class CodeSource(val name: String, private val textGetter: (String) -> String) {

    private var textRef: SoftReference<String> = SoftReference(null)

    /**
     * The source code
     */
    val text: String
        get() = textRef.get() ?: textGetter(name).also { textRef = SoftReference(it) }

    override fun equals(other: Any?) = other is CodeSource && other.name == name

    override fun hashCode() = name.hashCode()

    override fun toString() = "CodeSource($name)"

    fun mapText(transform: (String) -> String) = CodeSource(name) { transform(textGetter(it)) }

    companion object {
        /**
         * Creates a [CodeSource] from a string.
         *
         * @param name The name of the source.
         * @param text The source code.
         * @return The created [CodeSource].
         */
        fun constant(name: String, text: String) = CodeSource(name) { text }

        /**
         * Creates a [CodeSource] from a [Path]. Will use the base filename as the name.
         *
         * @param path The path to the source.
         * @return The created [CodeSource].
         */
        fun file(path: Path) = CodeSource(path.fileName.toString()) { path.readText() }

        /**
         * Creates a [CodeSource] from a resource. Will use the base filename as the name.
         *
         * @param path The path to the resource.
         * @param contextClass The class to use for the resource lookup. Defaults to [CodeSource].
         * @return The created [CodeSource].
         */
        fun resource(path: String, contextClass: Class<*> = CodeSource::class.java) =
            CodeSource(Path(path).fileName.toString()) {
                val resource = contextClass.getResource(path)
                checkNotNull(resource) { "Resource not found: $path" }
                resource.readText()
            }
    }
}
