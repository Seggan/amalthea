package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.backend.compilation.AsmType
import io.github.seggan.amalthea.frontend.parsing.TypeName

sealed interface Type {

    val typeName: String
    val jvmType: String

    fun toTypeName(): TypeName = TypeName.Simple(typeName)

    fun isAssignableTo(other: Type): Boolean {
        return this == other || other == Any
    }

    enum class Primitive(
        override val typeName: String,
        override val jvmType: String,
        private vararg val assginable: Type
    ) : Type {
        DOUBLE("Double", "D"),
        FLOAT("Float", "F", DOUBLE),
        LONG("Long", "J", DOUBLE),
        INT("Int", "I", LONG, FLOAT),
        SHORT("Short", "S", INT),
        BYTE("Byte", "B", SHORT),
        BOOLEAN("Boolean", "Z"),
        CHAR("Char", "C"),
        STRING("String", "Ljava/lang/String;"),
        ;

        override fun isAssignableTo(other: Type): Boolean {
            return super.isAssignableTo(other) || assginable.any { it.isAssignableTo(other) }
        }

        override fun toString(): String = typeName
    }

    data object Unit : Type {
        override val typeName = "Unit"
        override val jvmType = "V"
        override fun toString(): String = typeName
    }

    data object Nothing : Type {
        override val typeName = "Nothing"
        override val jvmType = "V"

        override fun isAssignableTo(other: Type): Boolean {
            return true
        }

        override fun toString(): String = typeName
    }

    data object Any : Type {
        override val typeName = "Any"
        override val jvmType = "Ljava/lang/Object;"
        override fun toString(): String = typeName
    }

    data class Function(val args: List<Type>, val returnType: Type) : Type {
        override val typeName = "(${args.joinToString(", ")}) -> $returnType"
        override val jvmType = "(${args.joinToString("") { it.jvmType }})${returnType.jvmType}"

        override fun toTypeName(): TypeName.Function {
            return TypeName.Function(
                args.map { it.toTypeName() },
                returnType.toTypeName()
            )
        }

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

val Type.asmType: AsmType
    get() = AsmType.getType(jvmType)