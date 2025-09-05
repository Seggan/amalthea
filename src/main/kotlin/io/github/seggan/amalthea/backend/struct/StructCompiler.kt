package io.github.seggan.amalthea.backend.struct

import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.descriptor
import io.github.seggan.amalthea.frontend.typing.internalName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

class StructCompiler(private val queryEngine: QueryEngine) : Queryable<Key.CompileStruct, CompiledStruct> {
    override val keyType = Key.CompileStruct::class

    override fun query(key: Key.CompileStruct): CompiledStruct {
        val struct = key.struct
        val cw = ClassWriter(0)
        cw.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL,
            struct.internalName,
            null,
            "java/lang/Object",
            null
        )
        val dependencies = mutableSetOf<CompiledStruct>()
        for ((name, type) in struct.fields) {
            if (type is Type.Struct && type !in Type.builtinTypes) {
                dependencies += queryEngine[Key.CompileStruct(type)]
            }
            cw.visitField(
                Opcodes.ACC_PUBLIC,
                name,
                type.descriptor,
                null,
                null
            ).visitEnd()
        }

        val constructor = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            struct.constructorDescriptor,
            null,
            null
        )
        constructor.visitCode()
        constructor.visitVarInsn(Opcodes.ALOAD, 0)
        constructor.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "java/lang/Object",
            "<init>",
            "()V",
            false
        )
        var index = 1
        val endLabel = Label()
        for ((name, type) in struct.fields) {
            constructor.visitLocalVariable(
                name,
                type.descriptor,
                null,
                Label(),
                endLabel,
                index
            )
            constructor.visitVarInsn(Opcodes.ALOAD, 0)
            constructor.visitVarInsn(type.asmType.getOpcode(Opcodes.ILOAD), index)
            constructor.visitFieldInsn(
                Opcodes.PUTFIELD,
                struct.internalName,
                name,
                type.descriptor
            )
            index++
        }
        constructor.visitLabel(endLabel)
        constructor.visitInsn(Opcodes.RETURN)
        constructor.visitMaxs(2, struct.fields.size + 1)
        constructor.visitEnd()

        cw.visitEnd()
        val bytecode = cw.toByteArray()
        return CompiledStruct(struct, bytecode, dependencies)
    }
}