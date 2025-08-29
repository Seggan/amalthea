package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class HeaderResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveHeader, Pair<Signature, AstNode.FunctionDeclaration<Unit>>> {
    override val keyType = Key.ResolveHeader::class

    override fun query(key: Key.ResolveHeader): Pair<Signature, AstNode.FunctionDeclaration<Unit>> {
        val untypedAst = queryEngine[Key.UntypedAst(key.context)]
        for (function in untypedAst.declarations) {
            if (function.name == key.name && function.parameters.map { it.second.name } == key.args) {
                val paramTypes = function.parameters.map { (_, type) ->
                    queryEngine[Key.ResolveType(type.name, function.span.source.name)]
                }
                val returnType = queryEngine[Key.ResolveType(function.returnType.name, function.span.source.name)]
                val type = Type.Function(paramTypes, returnType)
                return Signature(function.name, type) to function
            }
        }
        throw Exception("Could not resolve header: ${key.name} with args ${key.args}")
    }
}