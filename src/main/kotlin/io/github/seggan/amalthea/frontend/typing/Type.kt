package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.backend.AsmType
import io.github.seggan.amalthea.backend.asmType
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.TypeName

sealed interface Type {

    val qName: QualifiedName
    val asmType: AsmType

    val isJavaVoid: Boolean
        get() = false

    fun asTypeName(): TypeName {
        return TypeName.Simple(qName)
    }

    fun isAssignableTo(other: Type): Boolean {
        return this == other || other == Any
    }

    enum class Primitive(
        name: String,
        override val asmType: AsmType,
        private val assginable: Type? = null
    ) : Type {
        DOUBLE("Double", AsmType.DOUBLE_TYPE),
        FLOAT("Float", AsmType.FLOAT_TYPE, DOUBLE),
        LONG("Long", AsmType.LONG_TYPE),
        INT("Int", AsmType.INT_TYPE, LONG),
        SHORT("Short", AsmType.SHORT_TYPE, INT),
        BYTE("Byte", AsmType.BYTE_TYPE, SHORT),
        BOOLEAN("Boolean", AsmType.BOOLEAN_TYPE),
        CHAR("Char", AsmType.CHAR_TYPE),
        STRING("String", asmType<String>()),
        ;

        override val qName = QualifiedName.amalthea(name)

        override fun isAssignableTo(other: Type): Boolean {
            return super.isAssignableTo(other) || assginable?.isAssignableTo(other) == true
        }

        override fun toString(): String = qName.toString()
    }

    data object Unit : Type {
        override val qName = QualifiedName.amalthea("Unit")
        override val asmType: AsmType = AsmType.VOID_TYPE
        override val isJavaVoid = true
        override fun toString(): String = qName.toString()
    }

    data object Nothing : Type {
        override val qName = QualifiedName.amalthea("Nothing")
        override val asmType: AsmType = AsmType.VOID_TYPE
        override val isJavaVoid = true

        override fun isAssignableTo(other: Type): Boolean {
            return true
        }

        override fun toString(): String = qName.toString()
    }

    data object Any : Type {
        override val qName = QualifiedName.amalthea("Any")
        override val asmType = asmType<Any>()
        override fun toString(): String = qName.toString()
    }

    data class Struct(override val qName: QualifiedName, val fields: List<Pair<String, Type>>) : Type {
        override val asmType: AsmType = AsmType.getObjectType(qName.internalName)

        val constructorDescriptor: String by lazy {
            val fieldTypes = fields.map { it.second.asmType }
            AsmType.getMethodDescriptor(AsmType.VOID_TYPE, *fieldTypes.toTypedArray())
        }

        override fun toString(): String = qName.toString()
    }

    data class Function(val args: List<Type>, val returnType: Type) : Type {
        override val qName: QualifiedName
            get() = throw IllegalStateException("Function types do not have a qualified name")

        override val asmType: AsmType = AsmType.getMethodType(returnType.asmType, *args.map { it.asmType }.toTypedArray())

        override fun asTypeName(): TypeName.Function {
            return TypeName.Function(args.map { it.asTypeName() }, returnType.asTypeName())
        }

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

val Type.internalName: String
    get() = asmType.internalName

val Type.descriptor: String
    get() = asmType.descriptor

val AstNode<TypeData>.resolvedType: Type
    get() = extra.type

fun getCommonSupertype(type: Type, other: Type): Type {
    if (type.isAssignableTo(other)) return other
    if (other.isAssignableTo(type)) return type
    throw IllegalArgumentException("No common supertype between $type and $other")
}