package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Intrinsics
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class FunctionCallResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveFunctionCall, Signature> {
    override val keyType = Key.ResolveFunctionCall::class

    override fun query(key: Key.ResolveFunctionCall): Signature {
        val args = key.args
        val qName = key.name
        val name = if (qName.pkg.isEmpty()) {
            queryEngine[Key.FindImport(qName.name, key.context)]
        } else {
            qName
        }
        for (intrinsic in Intrinsics.entries) {
            val signature = intrinsic.signature
            if (signature.name == name && signature.canCallWith(args)) {
                return signature
            }
        }

        val source = queryEngine[Key.ResolvePackage(name.pkg)]
        val untypedAst = queryEngine[Key.UntypedAst(source)]
        for (function in untypedAst.declarations) {
            if (function.name == name.name) {
                val header = queryEngine[Key.ResolveHeader(name.pkg, function)]
                if (header.canCallWith(args)) {
                    return header
                }
            }
        }
        throw AmaltheaException("Could not find function matching $name(${args.joinToString(", ")})")
    }
}