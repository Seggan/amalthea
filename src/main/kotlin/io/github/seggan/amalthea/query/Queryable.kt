package io.github.seggan.amalthea.query

import kotlin.reflect.KClass

interface Queryable<K : Key<V>, V : Any> {
    val keyType: KClass<K>

    fun query(key: K): V
}