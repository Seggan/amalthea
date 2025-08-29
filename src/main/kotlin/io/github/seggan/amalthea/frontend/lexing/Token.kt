package io.github.seggan.amalthea.frontend.lexing

import io.github.seggan.amalthea.frontend.Span
import org.intellij.lang.annotations.Language

data class Token(val type: Type, val text: String, val span: Span) {
    enum class Type(@Language("RegExp") pattern: String? = null, val ignore: Boolean = false) {
        WHITESPACE("[\\s\\n]+", ignore = true),
        LINE_COMMENT("//.*\\n", ignore = true),
        BLOCK_COMMENT(ignore = true) {
            override fun getLength(input: CharSequence): Int {
                if (!input.startsWith("/*")) return 0
                var index = 2
                var depth = 1
                while (index < input.length) {
                    if (input.startsWith("/*", index)) {
                        depth++
                        index += 2
                    } else if (input.startsWith("*/", index)) {
                        depth--
                        index += 2
                        if (depth == 0) return index
                    } else {
                        index++
                    }
                }
                return 0
            }
        },
        NUMBER("-?(0|[1-9][0-9]*)(\\.[0-9]+)?"),
        STRING {
            override fun getLength(input: CharSequence): Int {
                if (input.isEmpty() || input[0] != '"') return 0
                var index = 1
                while (index < input.length) {
                    if (input[index] == '\\') {
                        index++
                        index += if (input[index] == 'u') 5 else 1
                    } else if (input[index] == '"') {
                        return index + 1
                    } else {
                        index++
                    }
                }
                return 0
            }
        },
        IDENTIFIER("[A-Za-z_][A-Za-z0-9_]*"),

        PLUS("\\+"),
        MINUS("-"),
        STAR("\\*"),
        SLASH("/"),
        PERCENT("%"),
        CARET("\\^"),
        EXCLAMATION("!"),
        AMPERSAND("&"),
        DOUBLE_AMPERSAND("&&"),
        PIPE("\\|"),
        DOUBLE_PIPE("\\|\\|"),
        EQUAL("="),
        DOUBLE_EQUAL("=="),
        TRIPLE_EQUAL("==="),
        NOT_EQUAL("!="),
        NOT_DOUBLE_EQUAL("!=="),
        LESS("<"),
        LESS_EQUAL("<="),
        GREATER(">"),
        GREATER_EQUAL(">="),
        OPEN_PAREN("\\("),
        CLOSE_PAREN("\\)"),
        OPEN_BRACE("\\{"),
        CLOSE_BRACE("}"),
        OPEN_BRACKET("\\["),
        CLOSE_BRACKET("]"),
        COMMA(","),
        DOT("\\."),
        COLON(":"),
        DOUBLE_COLON("::"),
        SEMICOLON(";"),
        ARROW("->"),

        IN("in"),
        NULL("null"),
        TRUE("true"),
        FALSE("false"),

        FUN("fun"),
        VAL("val"),
        VAR("var"),
        IF("if"),
        ELSE("else"),
        WHILE("while"),
        FOR("for"),
        RETURN("return"),
        BREAK("break"),
        CONTINUE("continue"),
        STRUCT("struct"),
        TRAIT("trait"),
        IMPL("impl"),

        ERROR {
            override fun getLength(input: CharSequence): Int = 0
        }
        ;

        private val pattern = pattern?.toRegex()

        open fun getLength(input: CharSequence): Int {
            checkNotNull(pattern)
            return pattern.matchAt(input, 0)?.value?.length ?: 0
        }
    }

    override fun toString(): String {
        return "${type.name}('$text')"
    }
}