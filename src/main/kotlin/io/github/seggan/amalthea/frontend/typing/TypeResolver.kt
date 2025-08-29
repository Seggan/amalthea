package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveType, Type> {
    override val keyType = Key.ResolveType::class

    override fun query(key: Key.ResolveType): Type {
        val typeName = key.type
        if (typeName is TypeName.Simple) {
            for (primitive in Type.Primitive.entries) {
                if (typeName.name == primitive.typeName) {
                    return primitive
                }
            }
        }
        return when (typeName) {
            is TypeName.Simple -> when (typeName.name) {
                "Nothing" -> Type.Nothing
                "Any" -> Type.Any
                "Unit" -> Type.Unit
                else -> throw AmaltheaException("Unknown type: ${typeName.name}", mutableListOf())
            }

            is TypeName.Function -> {
                val paramTypes = typeName.args.map { queryEngine[Key.ResolveType(it, key.context)] }
                val returnType = queryEngine[Key.ResolveType(typeName.returnType, key.context)]
                Type.Function(paramTypes, returnType)
            }
        }
    }
}