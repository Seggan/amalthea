package io.github.seggan.amalthea.backend

import io.github.seggan.amalthea.frontend.typing.Signature
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class CompiledFunction(
    val signature: Signature,
    val code: DeferredMethodVisitor,
    val dependencies: Set<CompiledFunction>
) {
    fun createIn(cv: ClassVisitor) {
        val mv = cv.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
            signature.name.name,
            if (signature.name.name == "main") "([Ljava/lang/String;)V" else signature.type.jvmType,
            null,
            null
        )
        mv.visitCode()
        code.applyDeferments(mv)
        mv.visitMaxs(0, 0) // let ASM compute stack and locals
        mv.visitEnd()
    }
}