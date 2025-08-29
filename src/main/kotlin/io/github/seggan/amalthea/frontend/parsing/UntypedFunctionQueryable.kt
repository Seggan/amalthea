package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class UntypedFunctionQueryable(private val queryEngine: QueryEngine) :
    Queryable<Key.UntypedFunction, AstNode.FunctionDeclaration<Unit>> {

    override val keyType = Key.UntypedFunction::class

    override fun query(key: Key.UntypedFunction): AstNode.FunctionDeclaration<Unit> {
        val functions = mutableMapOf<AstNode.FunctionDeclaration<Unit>, String>()
        for (source in queryEngine.sources) {
            val ast = queryEngine[Key.UntypedAst(source.name)]
            for (function in ast.declarations) {
                if (function.name == key.name && function.parameters.map { it.second } == key.paramTypes) {
                    functions.put(function, source.name)
                }
            }
        }
        if (functions.size == 1) {
            return functions.keys.single()
        }

        val signature = "${key.name}(${key.paramTypes.joinToString(", ")})"
        if (functions.isEmpty()) {
            throw AmaltheaException("Function $signature not found", mutableListOf())
        }
        throw AmaltheaException(
            "Function $signature is ambiguous and found in multiple sources: ${functions.values.joinToString(", ")}",
            mutableListOf()
        )
    }
}