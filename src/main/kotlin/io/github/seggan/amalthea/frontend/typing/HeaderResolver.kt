package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class HeaderResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveHeader, Pair<Signature, AstNode.FunctionDeclaration<Unit>>> {
    override val keyType = Key.ResolveHeader::class

    override fun query(key: Key.ResolveHeader): Pair<Signature, AstNode.FunctionDeclaration<Unit>> {
        val source = queryEngine[Key.ResolvePackage(key.name.pkg)]
        val untypedAst = queryEngine[Key.UntypedAst(source)]
        for (function in untypedAst.declarations) {
            if (function.name == key.name.name && function.parameters.map { it.second.name } == key.type.args) {
                if (!checkReturns(function)) {
                    throw AmaltheaException("Function ${key.name} does not return on all paths", function.span)
                }
                val paramTypes = function.parameters.map { (_, type) ->
                    queryEngine[Key.ResolveType(type.name)]
                }
                val returnType = queryEngine[Key.ResolveType(function.returnType.name)]
                val type = Type.Function(paramTypes, returnType)
                return Signature(key.name, type) to function
            }
        }
        throw AmaltheaException("Could not resolve header: ${key.type}", mutableListOf())
    }

    private fun checkReturns(node: AstNode<*>): Boolean = when (node) {
        is AstNode.File -> node.declarations.all(::checkReturns)
        is AstNode.FunctionDeclaration -> checkReturns(node.body)
        is AstNode.Block -> node.statements.lastOrNull()?.let(::checkReturns) ?: false
        is AstNode.Expression -> false
        is AstNode.Return -> true
        is AstNode.Type -> false
    }
}