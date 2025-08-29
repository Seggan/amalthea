package io.github.seggan.amalthea.frontend.typing

sealed interface Type {

    val typeName: String

    fun isAssignableTo(other: Type): Boolean {
        return this == other || other == Any
    }

    enum class Primitive(override val typeName: String, private vararg val assginable: Type) : Type {
        DOUBLE("Double"),
        FLOAT("Float", DOUBLE),
        LONG("Long", DOUBLE),
        INT("Int", LONG, FLOAT),
        SHORT("Short", INT),
        BYTE("Byte", SHORT),
        BOOLEAN("Boolean"),
        CHAR("Char"),
        STRING("String"),
        ;

        override fun isAssignableTo(other: Type): Boolean {
            return super.isAssignableTo(other) || assginable.any { it.isAssignableTo(other) }
        }

        override fun toString(): String = typeName
    }

    data object Unit : Type {
        override val typeName = "Unit"
        override fun toString(): String = typeName
    }

    data object Nothing : Type {
        override val typeName = "Nothing"

        override fun isAssignableTo(other: Type): Boolean {
            return true
        }

        override fun toString(): String = typeName
    }

    data object Any : Type {
        override val typeName = "Any"
        override fun toString(): String = typeName
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

        override fun toString(): String = typeName
    }
}