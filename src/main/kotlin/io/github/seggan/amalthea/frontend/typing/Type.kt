package io.github.seggan.amalthea.frontend.typing

interface Type {

    val typeName: String

    fun isAssignableTo(other: Type): Boolean {
        return this == other || other == Any
    }

    enum class Primitive(override val typeName: String) : Type {
        BYTE("Byte"),
        SHORT("Short"),
        INT("Int"),
        LONG("Long"),
        FLOAT("Float"),
        DOUBLE("Double"),
        BOOLEAN("Boolean"),
        CHAR("Char"),
        STRING("String"),
        ;

        companion object {
            val typeNames = entries.map { it.typeName }.toSet()
        }
    }

    data object Unit : Type {
        override val typeName = "Unit"
    }

    data object Nothing : Type {
        override val typeName = "Nothing"

        override fun isAssignableTo(other: Type): Boolean {
            return true
        }
    }

    data object Any : Type {
        override val typeName = "Any"
    }

    data class Function(val args: List<Type>, val returnType: Type) : Type {
        override val typeName = "(${args.joinToString(", ")}) -> $returnType"

        override fun isAssignableTo(other: Type): Boolean {
            if (other !is Function) return super.isAssignableTo(other)
            if (args.size != other.args.size) return false
            for (i in args.indices) {
                if (!args[i].isAssignableTo(other.args[i])) return false
            }
            return returnType.isAssignableTo(other.returnType)
        }
    }
}