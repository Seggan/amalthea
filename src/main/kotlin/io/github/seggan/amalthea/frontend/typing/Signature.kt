package io.github.seggan.amalthea.frontend.typing

data class Signature(val name: String, val type: Type.Function) {
    override fun toString(): String = buildString {
        append(name)
        append("(")
        append(type.args.joinToString(", "))
        append("): ")
        append(type.returnType)
    }

    fun canCallWith(other: Signature): Boolean {
        if (name != other.name) return false
        if (type.args.size != other.type.args.size) return false
        for (i in type.args.indices) {
            if (!other.type.args[i].isAssignableTo(type.args[i])) return false
        }
        return true
    }
}
