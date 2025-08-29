package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.frontend.CodeSource
import kotlin.reflect.KClass

class QueryEngine(private val sources: List<CodeSource>) : Queryable<Key.CodeSource, CodeSource> {

    override val queryEngine = this
    override val keyType = Key.CodeSource::class

    override fun query(key: Key.CodeSource): CodeSource {
        return sources.first { it.name == key.source }
    }

    private val queryables = mutableMapOf<KClass<*>, Queryable<*, *>>(keyType to this)
    private val queryCache = mutableMapOf<Key<*>, Any>()

    fun register(constructor: (QueryEngine) -> Queryable<*, *>) {
        val queryable = constructor(this)
        val keyType = queryable.keyType
        check(keyType !in queryables) { "A queryable for key type $keyType is already registered" }
        queryables[keyType] = queryable
    }

    operator fun <V : Any> get(key: Key<V>): V {
        @Suppress("UNCHECKED_CAST")
        return queryCache.getOrPut(key) {
            val queryable = queryables[key.type] ?: error("No queryable registered for key type ${key.type}")
            (queryable as Queryable<Key<V>, V>).query(key)
        } as V
    }
}