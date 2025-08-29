package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.parsing.AstNode

sealed interface Key<V : Any> {

    data class CodeSource(val source: String) : Key<io.github.seggan.amalthea.frontend.CodeSource>

    data class Tokens(val source: String) : Key<List<Token>>

    data class UntypedAst(val source: String) : Key<AstNode.File<Unit>>

    data class UntypedFunction(val name: String, val paramTypes: List<AstNode.Type<Unit>>) :
        Key<AstNode.FunctionDeclaration<Unit>>
}