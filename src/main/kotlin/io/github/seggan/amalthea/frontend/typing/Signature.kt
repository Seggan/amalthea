package io.github.seggan.amalthea.frontend.typing

data class Signature(val name: String, val type: Type.Function) {
    override fun toString(): String = buildString {
        append(name)
        append("(")
        append(type.args.joinToString(", "))
        append("): ")
        append(type.returnType)
    }
}
