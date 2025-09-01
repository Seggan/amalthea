package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveType, Type> {
    override val keyType = Key.ResolveType::class

    override fun query(key: Key.ResolveType): Type {
        val typeName = key.type
        if (typeName is TypeName.Simple && typeName.qName.pkg.singleOrNull() == "amalthea") {
            for (builtin in Type.builtinTypes) {
                if (typeName.qName == builtin.qName) {
                    return builtin
                }
            }
        }
        return when (typeName) {
            is TypeName.Simple -> TODO()

            is TypeName.Function -> {
                val paramTypes = typeName.args.map { queryEngine[Key.ResolveType(it)] }
                val returnType = queryEngine[Key.ResolveType(typeName.returnType)]
                Type.Function(paramTypes, returnType)
            }
        }
    }
}