package io.github.seggan.amalthea.frontend.typing.function

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class FunctionFinder(private val queryEngine: QueryEngine) : Queryable<Key.FindFunctionBody, AstNode.Function<Unit>> {
    override val keyType = Key.FindFunctionBody::class

    override fun query(key: Key.FindFunctionBody): AstNode.Function<Unit> {
        val signature = key.signature
        val source = queryEngine[Key.ResolvePackage(signature.name.pkg)]
        val untypedAst = queryEngine[Key.UntypedAst(source)]
        for (function in untypedAst.functions) {
            if (queryEngine[Key.ResolveHeader(signature.name.pkg, function)] == signature) {
                return function
            }
        }
        throw AmaltheaException("Could not find function $signature")
    }
}