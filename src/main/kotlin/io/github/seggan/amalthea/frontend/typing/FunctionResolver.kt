package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Intrinsics
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class FunctionResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveFunction, Signature> {
    override val keyType = Key.ResolveFunction::class

    override fun query(key: Key.ResolveFunction): Signature {
        val signature = key.signature
        for (intrinsic in Intrinsics.entries) {
            if (intrinsic.signature.canCallWith(signature)) {
                return intrinsic.signature
            }
        }
        val untypedAst = queryEngine[Key.UntypedAst(key.context)]
        for (function in untypedAst.declarations) {
            val (functionSignature, _) = queryEngine[Key.ResolveHeader(
                function.name,
                TypeName.Function(
                    function.parameters.map { it.second.name },
                    function.returnType.name
                ),
                key.context
            )]
            if (functionSignature.canCallWith(signature)) {
                return functionSignature
            }
        }
        throw AmaltheaException(
            "Could find function matching '$signature'",
            mutableListOf()
        )
    }
}