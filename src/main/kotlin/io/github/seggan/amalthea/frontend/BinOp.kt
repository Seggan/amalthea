package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.lexing.Token.Type.*
import io.github.seggan.amalthea.frontend.typing.Type

sealed class BinOp(val tokenType: Token.Type) {
//    ADD, SUB, MUL, DIV, MOD,
//    SHIFT_LEFT, SHIFT_RIGHT, USHIFT_RIGHT, BIT_AND, BIT_OR, BIT_XOR,
//    EQ, NOT_EQ, IS, NOT_IS, LT, LT_EQ, GT, GT_EQ,
//    AND, OR,
//    IN, NOT_IN;

    abstract fun checkType(left: Type, right: Type): Type

    sealed class NumberOp(tokenType: Token.Type) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(Type.Primitive.DOUBLE) && right.isAssignableTo(Type.Primitive.DOUBLE)) {
                return if (left.isAssignableTo(right)) right else left
            }
            throw AmaltheaException("Cannot perform '${this.tokenType}' on $left and $right", mutableListOf())
        }
    }

    data object Add : NumberOp(PLUS)
    data object Sub : NumberOp(MINUS)
    data object Mul : NumberOp(STAR)
    data object Div : NumberOp(SLASH)
    data object Mod : NumberOp(PERCENT)

    sealed class BitOp(tokenType: Token.Type) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(Type.Primitive.LONG) && right.isAssignableTo(Type.Primitive.LONG)) {
                return if (left.isAssignableTo(right)) right else left
            }
            throw AmaltheaException("Cannot perform '${this.tokenType}' on $left and $right", mutableListOf())
        }
    }

    data object ShiftLeft : BitOp(DOUBLE_LESS)
    data object ShiftRight : BitOp(DOUBLE_GREATER)
    data object UShiftRight : BitOp(TRIPLE_GREATER)
    data object BitAnd : BitOp(AMPERSAND)
    data object BitOr : BitOp(PIPE)
    data object BitXor : BitOp(CARET)

    sealed class EqCmpOp(tokenType: Token.Type) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(right) || right.isAssignableTo(left)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot perform '${this.tokenType}' on $left and $right", mutableListOf())
        }
    }

    data object Eq : EqCmpOp(DOUBLE_EQUAL)
    data object NotEq : EqCmpOp(NOT_DOUBLE_EQUAL)
    data object Is : EqCmpOp(TRIPLE_EQUAL)
    data object NotIs : EqCmpOp(NOT_TRIPLE_EQUAL)
    data object Lt : EqCmpOp(LESS)
    data object LtEq : EqCmpOp(LESS_EQUAL)
    data object Gt : EqCmpOp(GREATER)
    data object GtEq : EqCmpOp(GREATER_EQUAL)

    sealed class AndOrOp(tokenType: Token.Type) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(Type.Primitive.BOOLEAN) && right.isAssignableTo(Type.Primitive.BOOLEAN)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot perform '${this.tokenType}' on $left and $right", mutableListOf())
        }
    }

    data object And : AndOrOp(DOUBLE_AMPERSAND)
    data object Or : AndOrOp(DOUBLE_PIPE)
}