package io.github.seggan.amalthea.backend

import io.github.seggan.amalthea.backend.function.DeferredMethodVisitor
import io.github.seggan.amalthea.frontend.typing.Type
import org.objectweb.asm.Opcodes.INVOKESTATIC

typealias AsmType = org.objectweb.asm.Type

inline fun <reified T> asmType(): AsmType = AsmType.getType(T::class.java)
inline fun <reified T> asmTypeName(): String = AsmType.getInternalName(T::class.java)
inline fun <reified T> asmDescriptor(): String = AsmType.getDescriptor(T::class.java)

fun Type.promoteSmallToInt(): Type {
    return if (this == Type.Primitive.BYTE || this == Type.Primitive.SHORT) Type.Primitive.INT else this
}

fun DeferredMethodVisitor.boxConditional(provided: Type, expected: Type) {
    if (!(provided is Type.Primitive && expected !is Type.Primitive)) return
    when (provided) {
        Type.Primitive.BYTE -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Byte",
            "valueOf",
            "(B)Ljava/lang/Byte;",
            false
        )

        Type.Primitive.SHORT -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Short",
            "valueOf",
            "(S)Ljava/lang/Short;",
            false
        )

        Type.Primitive.INT -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Integer",
            "valueOf",
            "(I)Ljava/lang/Integer;",
            false
        )

        Type.Primitive.LONG -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Long",
            "valueOf",
            "(J)Ljava/lang/Long;",
            false
        )

        Type.Primitive.FLOAT -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Float",
            "valueOf",
            "(F)Ljava/lang/Float;",
            false
        )

        Type.Primitive.DOUBLE -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Double",
            "valueOf",
            "(D)Ljava/lang/Double;",
            false
        )

        Type.Primitive.BOOLEAN -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Boolean",
            "valueOf",
            "(Z)Ljava/lang/Boolean;",
            false
        )

        Type.Primitive.CHAR -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Character",
            "valueOf",
            "(C)Ljava/lang/Character;",
            false
        )

        else -> {}
    }
}