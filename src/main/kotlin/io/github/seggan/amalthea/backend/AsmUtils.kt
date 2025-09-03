package io.github.seggan.amalthea.backend

import io.github.seggan.amalthea.frontend.typing.Type

typealias AsmType = org.objectweb.asm.Type

inline fun <reified T> asmType(): AsmType = AsmType.getType(T::class.java)
inline fun <reified T> asmTypeName(): String = AsmType.getInternalName(T::class.java)
inline fun <reified T> asmDescriptor(): String = AsmType.getDescriptor(T::class.java)

fun Type.promoteSmallToInt(): Type {
    return if (this == Type.Primitive.BYTE || this == Type.Primitive.SHORT) Type.Primitive.INT else this
}