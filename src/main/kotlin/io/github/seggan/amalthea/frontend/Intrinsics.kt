package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.backend.compilation.AsmType
import io.github.seggan.amalthea.backend.compilation.DeferredMethodVisitor
import io.github.seggan.amalthea.backend.compilation.asmDescriptor
import io.github.seggan.amalthea.backend.compilation.asmTypeName
import io.github.seggan.amalthea.frontend.typing.Signature
import io.github.seggan.amalthea.frontend.typing.Type
import org.objectweb.asm.Opcodes
import java.io.PrintStream

enum class Intrinsics(val signature: Signature) {
    PRINTLN(Signature("println", Type.Function(listOf(Type.Any), Type.Unit))) {
        override fun compile(mv: DeferredMethodVisitor) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, asmTypeName<System>(), "out", asmDescriptor<PrintStream>())
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                asmTypeName<PrintStream>(),
                "println",
                AsmType.getMethodDescriptor(AsmType.VOID_TYPE, AsmType.getType(Any::class.java)),
                false
            )
        }
    };

    abstract fun compile(mv: DeferredMethodVisitor)
}