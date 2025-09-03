package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.frontend.CodeSource
import kotlin.reflect.KClass

class QueryEngine(val sources: List<CodeSource>) {

    private val queryables = mutableMapOf<KClass<*>, Queryable<*, *>>()
    private val queryCache = mutableMapOf<Key<*>, Any?>()

    fun register(constructor: (QueryEngine) -> Queryable<*, *>) {
        val queryable = constructor(this)
        val keyType = queryable.keyType
        check(keyType !in queryables) { "A queryable for key type $keyType is already registered" }
        queryables[keyType] = queryable
    }

    private var logDepth = 0

    operator fun <V> get(key: Key<V>): V {
        println("${"  ".repeat(logDepth++)}$key")
        @Suppress("UNCHECKED_CAST")
        val result = queryCache.getOrPut(key) {
            val queryable = queryables[key::class] ?: error("No queryable registered for key ${key::class}")
            (queryable as Queryable<Key<V>, V>).query(key)
        } as V
        logDepth--
        return result
    }
}