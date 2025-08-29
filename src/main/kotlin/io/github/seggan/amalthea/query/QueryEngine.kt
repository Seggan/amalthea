package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.frontend.CodeSource
import kotlin.reflect.KClass

class QueryEngine(val sources: List<CodeSource>) : Queryable<Key.CodeSource, CodeSource> {

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

    private var logDepth = 0

    operator fun <V : Any> get(key: Key<V>): V {
        @Suppress("UNCHECKED_CAST")
        return queryCache.getOrPut(key) {
            println("${"  ".repeat(logDepth++)}$key")
            val queryable = queryables[key::class] ?: error("No queryable registered for key ${key::class}")
            val result = (queryable as Queryable<Key<V>, V>).query(key)
            logDepth--
            result
        } as V
    }
}