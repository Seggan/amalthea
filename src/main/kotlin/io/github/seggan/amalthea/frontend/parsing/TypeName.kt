package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.QualifiedName

sealed interface TypeName {

    val name: String

    data class Simple(val qName: QualifiedName) : TypeName {
        override val name = qName.toString()
    }

    data class Function(val args: List<TypeName>, val returnType: TypeName) : TypeName {
        override val name = "(${args.joinToString(", ") { it.name }}): ${returnType.name}"
    }
}