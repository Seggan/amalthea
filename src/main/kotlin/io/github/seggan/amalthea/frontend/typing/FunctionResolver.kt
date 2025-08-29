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
        functionLoop@ for (function in untypedAst.declarations) {
            if (function.name == key.name && function.parameters.size == key.args.size) {
                val paramTypes = function.parameters.map { (_, type) ->
                    queryEngine[ResolveType(type.name, function.span.source.name)]
                }
                for (i in paramTypes.indices) {
                    if (!key.args[i].isAssignableTo(paramTypes[i])) {
                        continue@functionLoop
                    }
                }
                val returnType = queryEngine[ResolveType(function.returnType.name, function.span.source.name)]
                return Signature(function.name, Function(paramTypes, returnType))
            }
        }
        throw AmaltheaException("Could find function matching '${key.name}(${key.args.joinToString(", ")})'", mutableListOf())
    }
}