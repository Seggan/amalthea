package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Intrinsics
import io.github.seggan.amalthea.frontend.typing.Type.Function
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.Key.ResolveType
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class FunctionResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveFunctionCall, Signature> {
    override val keyType = Key.ResolveFunctionCall::class

    override fun query(key: Key.ResolveFunctionCall): Signature {
        val args = key.args
        for (intrinsic in Intrinsics.entries) {
            if (intrinsic.signature.name == key.name && intrinsic.signature.canCallWith(args)) {
                return intrinsic.signature
            }
        }
        val source = queryEngine[Key.ResolvePackage(key.name.pkg)]
        val untypedAst = queryEngine[Key.UntypedAst(source)]
        for (function in untypedAst.declarations) {
            val paramTypes = function.parameters.map { (_, type) ->
                queryEngine[ResolveType(type.name)]
            }
            val returnType = queryEngine[ResolveType(function.returnType.name)]
            val type = Function(paramTypes, returnType)
            if (function.name == key.name.name && type.canCallWith(args)) {
                return Signature(key.name, type)
            }
        }
        throw AmaltheaException("Could not find function matching '$args'")
    }
}