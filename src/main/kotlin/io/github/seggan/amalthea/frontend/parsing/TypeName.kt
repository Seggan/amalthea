package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.QualifiedName

sealed interface TypeName {

    data class Simple(val qName: QualifiedName) : TypeName {
        override fun toString() = qName.toString()
    }

    data class Function(val args: List<TypeName>, val returnType: TypeName) : TypeName {
        override fun toString() = "(${args.joinToString(", ")}): $returnType"
    }
}