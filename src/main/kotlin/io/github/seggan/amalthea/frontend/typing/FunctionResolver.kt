package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.typing.Type.Function
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.Key.ResolveType
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class FunctionResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveFunction, Signature> {
    override val keyType = Key.ResolveFunction::class

    override fun query(key: Key.ResolveFunction): Signature {
        val untypedAst = queryEngine[Key.UntypedAst(key.context)]
        val signature = key.signature
        for (function in untypedAst.declarations) {
            if (function.name == signature.name && function.parameters.size == signature.type.args.size) {
                val paramTypes = function.parameters.map { (_, type) ->
                    queryEngine[ResolveType(type.name, function.span.source.name)]
                }
                val returnType = queryEngine[ResolveType(function.returnType.name, function.span.source.name)]
                val functionType = Function(paramTypes, returnType)
                if (signature.type.isAssignableTo(functionType)) {
                    return Signature(function.name, functionType)
                }
            }
        }
        throw AmaltheaException("Could find function matching $signature", mutableListOf())
    }
}