package io.github.seggan.amalthea.frontend.lexing

import io.github.seggan.amalthea.frontend.Span
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class Lexer(private val queryEngine: QueryEngine) : Queryable<Key.Tokens, List<Token>> {

    override val keyType = Key.Tokens::class

    override fun query(key: Key.Tokens): List<Token> {
        val source = queryEngine[Key.CodeSource(key.source)]
        val builder = StringBuilder(source.text)
        val tokens = mutableListOf<Token>()
        var pos = 0
        while (builder.isNotEmpty()) {
            var longestMatch: Token? = null
            for (type in Token.Type.entries) {
                val matchLength = type.getLength(builder)
                if (matchLength != 0 && (longestMatch == null || matchLength >= longestMatch.span.length)) {
                    longestMatch = Token(
                        type,
                        builder.substring(0, matchLength),
                        Span(pos, pos + matchLength, source)
                    )
                }
            }
            if (longestMatch != null) {
                tokens.add(longestMatch)
                builder.delete(0, longestMatch.span.length)
                pos += longestMatch.span.length
            } else {
                // No match, skip one character
                tokens.add(Token(Token.Type.ERROR, builder[0].toString(), Span(pos, pos + 1, source)))
                builder.deleteCharAt(0)
                pos++
            }
        }
        return tokens.filterNot { it.type.ignore }
    }
}