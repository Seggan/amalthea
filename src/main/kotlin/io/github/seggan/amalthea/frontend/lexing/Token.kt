package io.github.seggan.amalthea.frontend.lexing

import io.github.seggan.amalthea.frontend.Span
import org.intellij.lang.annotations.Language

data class Token(val type: Type, val text: String, val span: Span) {
    enum class Type(val humanName: String, @Language("RegExp") pattern: String? = null, val ignore: Boolean = false) {
        WHITESPACE("whitespace", "[\\s\\n]+", ignore = true),
        LINE_COMMENT("a line comment", "//.*\\n", ignore = true),
        BLOCK_COMMENT("a block comment", ignore = true) {
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
        NUMBER("a number", "-?(0|[1-9][0-9]*)(\\.[0-9]+)?"),
        STRING("a string") {
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
        IDENTIFIER("an identifier", "[A-Za-z_][A-Za-z0-9_]*"),

        PLUS("'+'", "\\+"),
        MINUS("'-'", "-"),
        STAR("'*'", "\\*"),
        SLASH("'/'", "/"),
        PERCENT("'%'", "%"),
        CARET("'^'", "\\^"),
        EXCLAMATION("'!'", "!"),
        AMPERSAND("'&'", "&"),
        DOUBLE_AMPERSAND("'&&'", "&&"),
        PIPE("'|'", "\\|"),
        DOUBLE_PIPE("'||'", "\\|\\|"),
        EQUAL("'='", "="),
        DOUBLE_EQUAL("'=='", "=="),
        TRIPLE_EQUAL("'==='", "==="),
        NOT_DOUBLE_EQUAL("'!='", "!="),
        NOT_TRIPLE_EQUAL("'!=='", "!=="),
        LESS("'<'", "<"),
        DOUBLE_LESS("'<<'", "<<"),
        LESS_EQUAL("'<='", "<="),
        GREATER("'>'", ">"),
        DOUBLE_GREATER("'>>'", ">>"),
        TRIPLE_GREATER("'>>>'", ">>>"),
        GREATER_EQUAL("'>='", ">="),
        OPEN_PAREN("'('", "\\("),
        CLOSE_PAREN("')'", "\\)"),
        OPEN_BRACE("'{'", "\\{"),
        CLOSE_BRACE("'}'", "}"),
        OPEN_BRACKET("'['", "\\["),
        CLOSE_BRACKET("']'", "]"),
        COMMA("','", ","),
        DOT("'.'", "\\."),
        COLON("':'", ":"),
        DOUBLE_COLON("'::'", "::"),
        SEMICOLON("';'", ";"),
        TILDE("'~'", "~"),

        TRUE("'true'", "true"),
        FALSE("'false'", "false"),

        MUT("'mut'", "mut"),
        OWN("'own'", "own"),

        PACKAGE("'package'", "package"),
        IMPORT("'import'", "import"),

        FUN("'fun'", "fun"),
        VAR("'var'", "var"),
        RETURN("'return'", "return"),

        IF("'if'", "if"),
        ELSE("'else'", "else"),

        ERROR("error") {
            override fun getLength(input: CharSequence): Int = 0
        }
        ;

        private val pattern = pattern?.toRegex()

        open fun getLength(input: CharSequence): Int {
            checkNotNull(pattern)
            return pattern.matchAt(input, 0)?.value?.length ?: 0
        }

        override fun toString(): String {
            return humanName
        }
    }

    override fun toString(): String {
        return "${type.name}('$text')"
    }
}