package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.backend.AsmType
import io.github.seggan.amalthea.backend.asmDescriptor
import io.github.seggan.amalthea.backend.asmType
import io.github.seggan.amalthea.backend.asmTypeName
import io.github.seggan.amalthea.backend.function.DeferredMethodVisitor
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.function.Signature
import org.objectweb.asm.Opcodes
import java.io.InputStream
import java.io.PrintStream
import java.util.Scanner

enum class Intrinsics(val signature: Signature) {
    PRINTLN(Signature(QualifiedName.amalthea("println"), Type.Function(listOf(Type.Any), Type.Unit))) {
        override fun compile(mv: DeferredMethodVisitor) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, asmTypeName<System>(), "out", asmDescriptor<PrintStream>())
            mv.visitInsn(Opcodes.SWAP)
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                asmTypeName<PrintStream>(),
                "println",
                AsmType.getMethodDescriptor(AsmType.VOID_TYPE, asmType<Any>()),
                false
            )
        }
    },
    READLN(Signature(QualifiedName.amalthea("readln"), Type.Function(emptyList(), Type.Primitive.STRING))) {
        override fun compile(mv: DeferredMethodVisitor) {
            mv.visitTypeInsn(Opcodes.NEW, asmTypeName<Scanner>())
            mv.visitInsn(Opcodes.DUP)
            mv.visitFieldInsn(
                Opcodes.GETSTATIC,
                asmTypeName<System>(),
                "in",
                asmDescriptor<InputStream>()
            )
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                asmTypeName<Scanner>(),
                "<init>",
                AsmType.getMethodDescriptor(AsmType.VOID_TYPE, asmType<InputStream>()),
                false
            )
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                asmTypeName<Scanner>(),
                "nextLine",
                AsmType.getMethodDescriptor(asmType<String>()),
                false
            )
        }
    }
    ;

    abstract fun compile(mv: DeferredMethodVisitor)
}