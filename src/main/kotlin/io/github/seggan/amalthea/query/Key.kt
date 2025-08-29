package io.github.seggan.amalthea.query

import kotlin.reflect.KClass

sealed interface Key<V : Any> {

    val type: KClass<V>

    data class CodeSource(val source: String) : Key<io.github.seggan.amalthea.frontend.CodeSource> {
        override val type = io.github.seggan.amalthea.frontend.CodeSource::class
    }
}