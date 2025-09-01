package io.github.seggan.amalthea.backend.compilation

import org.objectweb.asm.Type

typealias AsmType = Type

inline fun <reified T> asmTypeName(): String = AsmType.getInternalName(T::class.java)
inline fun <reified T> asmDescriptor(): String = AsmType.getDescriptor(T::class.java)