package io.github.seggan.amalthea.frontend.typing.function

import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class HeaderResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolveHeader, Signature> {
    override val keyType = Key.ResolveHeader::class

    override fun query(key: Key.ResolveHeader): Signature {
        val function = key.function
        val source = function.span.source
        val paramTypes = function.parameters.map { (_, type) ->
            queryEngine[Key.ResolveType(type.name, source)]
        }
        val returnType = queryEngine[Key.ResolveType(function.returnType.name, source)]
        val type = Type.Function(paramTypes, returnType)
        val signature = Signature(QualifiedName(key.pkg, function.name), type)
        return signature
    }
}