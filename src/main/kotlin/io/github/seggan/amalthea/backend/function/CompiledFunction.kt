package io.github.seggan.amalthea.backend.function

import io.github.seggan.amalthea.backend.OutputClass
import io.github.seggan.amalthea.backend.struct.CompiledStruct
import io.github.seggan.amalthea.frontend.typing.function.Signature
import io.github.seggan.amalthea.frontend.typing.internalName
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class CompiledFunction(
    val signature: Signature,
    val code: DeferredMethodVisitor,
    functionsUsed: Set<CompiledFunction>,
    structsUsed: Set<CompiledStruct>,
    val outputClass: OutputClass
) {

    val functionsUsed: Set<CompiledFunction> by lazy {
        functionsUsed.flatMapTo(mutableSetOf()) { it.functionsUsed } + functionsUsed + this
    }

    val structsUsed: Set<CompiledStruct> by lazy {
        structsUsed.flatMapTo(mutableSetOf()) { it.dependencies } + structsUsed
    }

    fun createIn(cv: ClassVisitor) {
        val mv = cv.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
            signature.name.name,
            if (signature.name.name == "main") "([Ljava/lang/String;)V" else signature.type.internalName,
            null,
            null
        )
        mv.visitCode()
        code.applyDeferments(mv)
        mv.visitMaxs(0, 0) // let ASM compute stack and locals
        mv.visitEnd()
    }

    override fun equals(other: Any?): Boolean = other is CompiledFunction && signature == other.signature
    override fun hashCode(): Int = signature.hashCode()
}