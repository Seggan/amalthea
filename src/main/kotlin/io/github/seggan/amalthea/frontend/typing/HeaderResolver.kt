package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class HeaderResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveHeader, Signature> {
    override val keyType = Key.ResolveHeader::class

    override fun query(key: Key.ResolveHeader): Signature {
        val function = key.function
        val source = function.span.source
        val paramTypes = function.parameters.map { (_, type) ->
            queryEngine[Key.ResolveType(type.name, source)]
        }
        val returnType = queryEngine[Key.ResolveType(function.returnType.name, source)]
        val type = Type.Function(paramTypes, returnType)
        val signature = Signature(QualifiedName(key.pkg, function.name), type)
        if (!checkReturns(function)) {
            throw AmaltheaException("Function $signature does not return on all paths", function.span)
        }
        return signature
    }

    private fun checkReturns(node: AstNode<*>): Boolean = when (node) {
        is AstNode.File -> node.declarations.all(::checkReturns)
        is AstNode.Import -> false
        is AstNode.FunctionDeclaration -> checkReturns(node.body)
        is AstNode.Block -> node.statements.any(::checkReturns)
        is AstNode.Expression -> false
        is AstNode.Return -> true
        is AstNode.Type -> false
        is AstNode.VariableAssignment -> false
        is AstNode.VariableDeclaration -> false
    }
}