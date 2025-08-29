package io.github.seggan.amalthea.frontend

import kotlin.math.max
import kotlin.math.min

data class Span(val start: Int, val endExclusive: Int, val source: CodeSource) {

    /**
     * The length of the span.
     */
    val length = endExclusive - start

    private val lineAndCol by lazy {
        val lines = source.text.split("\n")
        var pos = start
        var line = 0
        while (line < lines.size && pos > lines[line].length) {
            pos -= lines[line].length + 1
            line++
        }
        LineAndCol(line + 1, pos + 1)
    }

    val line get() = lineAndCol.line
    val col get() = lineAndCol.col

    /**
     * Merges this span with another span.
     *
     * @param other The other span.
     * @return The merged span.
     */
    operator fun plus(other: Span) = Span(min(start, other.start), max(endExclusive, other.endExclusive), source)

    /**
     * Returns a "fancy" string representation of the span as used in error messages.
     *
     * @return A "fancy" string representation of the span.
     */
    fun fancyToString(): String {
        val lines = source.text.split("\n")
        val lineText = lines[line - 1]
        val sb = StringBuilder()
        sb.append("|> ").appendLine(lineText)
        sb.append(" ".repeat(col + 2))
        sb.append("^")
        if (length > 1) {
            sb.append("~".repeat(length - 2))
            sb.appendLine("^")
        }
        return sb.toString()
    }

    override fun toString(): String {
        return "${source.name}:${line}:${col}"
    }

    private data class LineAndCol(val line: Int, val col: Int)
}
