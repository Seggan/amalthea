package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.backend.function.DeferredMethodVisitor
import io.github.seggan.amalthea.backend.promoteSmallToInt
import io.github.seggan.amalthea.frontend.typing.Type
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
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitInsn(Opcodes.IXOR)
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