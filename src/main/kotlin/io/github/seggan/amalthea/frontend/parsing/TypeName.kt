package io.github.seggan.amalthea.frontend.parsing

sealed interface TypeName {

    val name: String

    data class Simple(override val name: String) : TypeName

    data class Function(val args: List<TypeName>, val returnType: TypeName) : TypeName {
        override val name = "(${args.joinToString(", ")}): ${returnType.name}"
    }
}