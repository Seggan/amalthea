package io.github.seggan.amalthea.frontend.typing

sealed interface TypeData {

    val type: Type

    data object None : TypeData {
        override val type = Type.Nothing
    }

    data class Basic(override val type: Type) : TypeData
}