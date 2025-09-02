package io.github.seggan.amalthea.frontend.typing

sealed interface TypeData {

    val type: Type

    data object None : TypeData {
        override val type = Type.Nothing
    }

    data class Basic(override val type: Type) : TypeData

    data class FunctionCall(val signature: Signature) : TypeData {
        override val type = signature.type.returnType
    }

    data class Variable(val variable: LocalVariable) : TypeData {
        override val type = variable.type
    }

    data class Function(val signature: Signature, val parameters: List<LocalVariable>) : TypeData {
        override val type = signature.type
    }
}