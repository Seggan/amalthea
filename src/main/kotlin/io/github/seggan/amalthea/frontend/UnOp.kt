package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.backend.compilation.DeferredMethodVisitor
import io.github.seggan.amalthea.backend.compilation.promoteSmallToInt
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.asmType
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes

enum class UnOp {
    NEG {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.DOUBLE)) {
                return type
            }
            throw AmaltheaException("Cannot apply unary '-' to type '$type'", mutableListOf())
        }

        override fun compile(mv: DeferredMethodVisitor, type: Type) {
            mv.visitInsn(type.promoteSmallToInt().asmType.getOpcode(Opcodes.INEG))
        }
    },
    NOT {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.BOOLEAN)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot apply unary '!' to type '$type'", mutableListOf())
        }

        override fun compile(mv: DeferredMethodVisitor, type: Type) {
            val labelTrue = Label()
            val labelEnd = Label()
            mv.visitJumpInsn(Opcodes.IFEQ, labelTrue)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitJumpInsn(Opcodes.GOTO, labelEnd)
            mv.visitLabel(labelTrue)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitLabel(labelEnd)
        }
    },
    PLUS {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.DOUBLE)) {
                return type
            }
            throw AmaltheaException("Cannot apply unary '+' to type '$type'", mutableListOf())
        }

        override fun compile(mv: DeferredMethodVisitor, type: Type) {
            // Unary plus is a no-op
        }
    },
    BIT_NOT {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.LONG)) {
                return type
            }
            throw AmaltheaException("Cannot apply unary '~' to type '$type'", mutableListOf())
        }

        override fun compile(mv: DeferredMethodVisitor, type: Type) {
            mv.visitInsn(type.asmType.getOpcode(Opcodes.ICONST_M1))
            mv.visitInsn(type.asmType.getOpcode(Opcodes.IXOR))
        }
    };

    abstract fun checkType(type: Type): Type

    abstract fun compile(mv: DeferredMethodVisitor, type: Type)
}