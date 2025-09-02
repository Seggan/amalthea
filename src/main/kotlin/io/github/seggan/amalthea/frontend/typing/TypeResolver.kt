package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveType, Type> {
    override val keyType = Key.ResolveType::class

    override fun query(key: Key.ResolveType): Type {
        val type = key.type
        if (type is TypeName.Simple && type.qName.pkg.singleOrNull() == "amalthea") {
            for (builtin in Type.builtinTypes) {
                if (type.qName == builtin.qName) {
                    return builtin
                }
            }
        }
        return when (type) {
            is TypeName.Simple -> {
                if (type.qName.pkg.isEmpty()) {
                    runCatching {
                        queryEngine[Key.ResolveType(TypeName.Simple(QualifiedName(listOf("amalthea"), type.qName.name)))]
                    }.getOrNull()?.let { return it }
                }
                TODO()
            }

            is TypeName.Function -> {
                val paramTypes = type.args.map { queryEngine[Key.ResolveType(it)] }
                val returnType = queryEngine[Key.ResolveType(type.returnType)]
                Type.Function(paramTypes, returnType)
            }
        }
    }
}