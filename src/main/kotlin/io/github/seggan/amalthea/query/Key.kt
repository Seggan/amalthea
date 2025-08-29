package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.frontend.typing.Signature
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.TypeData

sealed interface Key<V : Any> {

    data class Source(val source: String) : Key<CodeSource>

    data class Tokens(val source: String) : Key<List<Token>>

    data class UntypedAst(val source: String) : Key<AstNode.File<Unit>>

    data class ResolveType(val type: TypeName, val context: String) : Key<Type>

    data class ResolveHeader(
        val name: String,
        val args: List<TypeName>,
        val returnType: TypeName,
        val context: String
    ) : Key<Pair<Signature, AstNode.FunctionDeclaration<Unit>>>

    data class ResolveFunction(val signature: Signature, val context: String) : Key<Signature>

    data class TypeCheck(
        val name: String,
        val args: List<TypeName>,
        val returnType: TypeName,
        val context: String
    ) : Key<AstNode.FunctionDeclaration<TypeData>>
}