package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.QualifiedName

data class Signature(val name: QualifiedName, val type: Type.Function) {
    override fun toString(): String = buildString {
        append(name)
        append("(")
        append(type.args.joinToString(", "))
        append("): ")
        append(type.returnType)
    }

    fun canCallWith(args: List<Type>): Boolean {
        if (args.size != type.args.size) return false
        return args.zip(type.args).all { (given, expected) -> given.isAssignableTo(expected) }
    }
}
