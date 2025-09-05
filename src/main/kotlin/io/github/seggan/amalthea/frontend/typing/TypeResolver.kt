package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveType, Type> {
    override val keyType = Key.ResolveType::class

    override fun query(key: Key.ResolveType): Type {
        return when (val type = key.type) {
            is TypeName.Simple -> {
                val name = expandName(type.qName, key.context)
                for (builtin in Type.builtinTypes) {
                    if (builtin.qName == name) {
                        return builtin
                    }
                }
                findStruct(name)?.let { return it }
                throw AmaltheaException("Type '${type.qName}' not found")
            }

            is TypeName.Function -> {
                val paramTypes = type.args.map { queryEngine[Key.ResolveType(it, key.context)] }
                val returnType = queryEngine[Key.ResolveType(type.returnType, key.context)]
                Type.Function(paramTypes, returnType)
            }
        }
    }

    private fun expandName(name: QualifiedName, context: CodeSource): QualifiedName {
        return if (name.pkg.isEmpty()) queryEngine[Key.FindImport(name.name, context)] else name
    }

    private fun findStruct(name: QualifiedName): Type.Struct? {
        val source = queryEngine[Key.ResolvePackage(name.pkg)]
        val untypedAst = queryEngine[Key.UntypedAst(source)]
        for (struct in untypedAst.structs) {
            if (struct.name == name.name) {
                val fields = struct.fields.map { (fieldName, fieldType) ->
                    val resolvedType = queryEngine[Key.ResolveType(fieldType.name, source)]
                    fieldName to resolvedType
                }
                return Type.Struct(name, fields)
            }
        }
        return null
    }
}