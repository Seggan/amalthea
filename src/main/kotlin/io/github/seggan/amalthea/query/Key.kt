package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.frontend.lexing.Token

sealed interface Key<V : Any> {

    data class CodeSource(val source: String) : Key<io.github.seggan.amalthea.frontend.CodeSource>

    data class Tokens(val source: String) : Key<List<Token>>
}