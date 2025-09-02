package io.github.seggan.amalthea.backend.compilation

import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor

class DeferredMethodVisitor {

    private val deferments = mutableListOf<MethodVisitor.() -> Unit>()

    fun applyDeferments(applyTo: MethodVisitor) {
        for (deferment in deferments) {
            applyTo.deferment()
        }
    }

    fun visitLineNumber(line: Int, start: Label) {
        deferments += { visitLineNumber(line, start) }
    }

    fun visitInsn(opcode: Int) {
        deferments += { visitInsn(opcode) }
    }

    fun visitIntInsn(opcode: Int, operand: Int) {
        deferments += { visitIntInsn(opcode, operand) }
    }

    fun visitVarInsn(opcode: Int, varIndex: Int) {
        deferments += { visitVarInsn(opcode, varIndex) }
    }

    fun visitTypeInsn(opcode: Int, type: String) {
        deferments += { visitTypeInsn(opcode, type) }
    }

    fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
        deferments += { visitFieldInsn(opcode, owner, name, descriptor) }
    }

    fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean = false) {
        deferments += { visitMethodInsn(opcode, owner, name, descriptor, isInterface) }
    }

    fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any
    ) {
        deferments += { visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments) }
    }

    fun visitJumpInsn(opcode: Int, label: Label) {
        deferments += { visitJumpInsn(opcode, label) }
    }

    fun visitLabel(label: Label) {
        deferments += { visitLabel(label) }
    }

    fun visitLdcInsn(value: Any) {
        deferments += { visitLdcInsn(value) }
    }

    fun visitIincInsn(varIndex: Int, increment: Int) {
        deferments += { visitIincInsn(varIndex, increment) }
    }

    fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
        deferments += { visitTableSwitchInsn(min, max, dflt, *labels) }
    }

    fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
        deferments += { visitLookupSwitchInsn(dflt, keys, labels) }
    }

    fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
        deferments += { visitMultiANewArrayInsn(descriptor, numDimensions) }
    }

    fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String) {
        deferments += { visitTryCatchBlock(start, end, handler, type) }
    }

    fun visitLocalVariable(name: String, descriptor: String, signature: String?, start: Label, end: Label, index: Int) {
        deferments += { visitLocalVariable(name, descriptor, signature, start, end, index) }
    }
}