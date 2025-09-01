package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Intrinsics
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class FunctionResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveFunctionCall, Signature> {
    override val keyType = Key.ResolveFunctionCall::class

    override fun query(key: Key.ResolveFunctionCall): Signature {
        val args = key.args
        for (intrinsic in Intrinsics.entries) {
            if (intrinsic.signature.canCallWith(args)) {
                return intrinsic.signature
            }
        }
        val source = queryEngine[Key.ResolvePackage(key.name.pkg)]
        val untypedAst = queryEngine[Key.UntypedAst(source)]
        for (function in untypedAst.declarations) {
            val (functionSignature, _) = queryEngine[Key.ResolveHeader(
                key.name,
                TypeName.Function(
                    function.parameters.map { it.second.name },
                    function.returnType.name
                )
            )]
            if (functionSignature.canCallWith(args)) {
                return functionSignature
            }
        }
        throw AmaltheaException(
            "Could find function matching '$args'",
            mutableListOf()
        )
    }
}