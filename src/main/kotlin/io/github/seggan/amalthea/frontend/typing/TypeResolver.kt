package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveType, Type> {
    override val keyType = Key.ResolveType::class

    override fun query(key: Key.ResolveType): Type {
        return when (val type = key.type) {
            is TypeName.Simple -> {
                val qName = type.qName
                val name = if (qName.pkg.isEmpty()) {
                    queryEngine[Key.FindImport(qName.name, key.context)]
                } else {
                    qName
                }
                for (builtin in Type.builtinTypes) {
                    if (builtin.qName == name) {
                        return builtin
                    }
                }
                throw AmaltheaException("Type '$qName' not found")
            }

            is TypeName.Function -> {
                val paramTypes = type.args.map { queryEngine[Key.ResolveType(it, key.context)] }
                val returnType = queryEngine[Key.ResolveType(type.returnType, key.context)]
                Type.Function(paramTypes, returnType)
            }
        }
    }
}