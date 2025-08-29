package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.frontend.typing.Type

enum class UnOp {
    NEG {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.DOUBLE)) {
                return type
            }
            throw AmaltheaException("Cannot apply unary '-' to type '$type'", mutableListOf())
        }
    },
    NOT {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.BOOLEAN)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot apply unary '!' to type '$type'", mutableListOf())
        }
    },
    PLUS {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.DOUBLE)) {
                return type
            }
            throw AmaltheaException("Cannot apply unary '+' to type '$type'", mutableListOf())
        }
    },
    BIT_NOT {
        override fun checkType(type: Type): Type {
            if (type.isAssignableTo(Type.Primitive.LONG)) {
                return type
            }
            throw AmaltheaException("Cannot apply unary '~' to type '$type'", mutableListOf())
        }
    };

    abstract fun checkType(type: Type): Type
}