package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.backend.compilation.AsmType
import io.github.seggan.amalthea.frontend.QualifiedName

sealed interface Type {

    val qName: QualifiedName
    val jvmType: String

    fun isAssignableTo(other: Type): Boolean {
        return this == other || other == Any
    }

    enum class Primitive(
        name: String,
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

        override val qName = QualifiedName.amalthea(name)

        override fun isAssignableTo(other: Type): Boolean {
            return super.isAssignableTo(other) || assginable.any { it.isAssignableTo(other) }
        }

        override fun toString(): String = qName.toString()
    }

    data object Unit : Type {
        override val qName = QualifiedName.amalthea("Unit")
        override val jvmType = "V"
        override fun toString(): String = qName.toString()
    }

    data object Nothing : Type {
        override val qName = QualifiedName.amalthea("Nothing")
        override val jvmType = "V"

        override fun isAssignableTo(other: Type): Boolean {
            return true
        }

        override fun toString(): String = qName.toString()
    }

    data object Any : Type {
        override val qName = QualifiedName.amalthea("Any")
        override val jvmType = "Ljava/lang/Object;"
        override fun toString(): String = qName.toString()
    }

    data class Function(val args: List<Type>, val returnType: Type) : Type {
        override val qName: QualifiedName
            get() = throw IllegalStateException("Function types do not have a qualified name")

        override val jvmType = "(${args.joinToString("") { it.jvmType }})${returnType.jvmType}"

        override fun isAssignableTo(other: Type): Boolean {
            if (other !is Function) return super.isAssignableTo(other)
            if (args.size != other.args.size) return false
            for (i in args.indices) {
                if (!args[i].isAssignableTo(other.args[i])) return false
            }
            return returnType.isAssignableTo(other.returnType)
        }

        override fun toString(): String = "(${args.joinToString(", ")}) -> $returnType"
    }

    companion object {
        val builtinTypes by lazy { Primitive.entries + listOf(Unit, Nothing, Any) }
    }
}

val Type.asmType: AsmType
    get() = AsmType.getType(jvmType)