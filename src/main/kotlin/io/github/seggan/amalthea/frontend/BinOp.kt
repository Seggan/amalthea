package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.backend.compilation.DeferredMethodVisitor
import io.github.seggan.amalthea.backend.compilation.promoteSmallToInt
import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.lexing.Token.Type.*
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.asmType
import io.github.seggan.amalthea.frontend.typing.getCommonSupertype
import org.objectweb.asm.Opcodes.*

sealed class BinOp(val tokenType: Token.Type) {

    abstract fun checkType(left: Type, right: Type): Type

    open fun compile(mv: DeferredMethodVisitor, left: Type, right: Type) {
        TODO()
    }

    sealed class NumberOp(tokenType: Token.Type, private val op: Int) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (
                (left.isAssignableTo(Type.Primitive.LONG) && right.isAssignableTo(Type.Primitive.LONG)) ||
                (left.isAssignableTo(Type.Primitive.DOUBLE) && right.isAssignableTo(Type.Primitive.DOUBLE))
            ) {
                return getCommonSupertype(left, right)
            }
            throw AmaltheaException("Cannot perform ${this.tokenType} on $left and $right", mutableListOf())
        }

        override fun compile(mv: DeferredMethodVisitor, left: Type, right: Type) {
            val supertype = getCommonSupertype(left, right).promoteSmallToInt()
            mv.visitInsn(supertype.asmType.getOpcode(op))
        }
    }

    data object Add : NumberOp(PLUS, IADD)
    data object Sub : NumberOp(MINUS, ISUB)
    data object Mul : NumberOp(STAR, IMUL)
    data object Div : NumberOp(SLASH, IDIV)
    data object Mod : NumberOp(PERCENT, IREM)

    sealed class BitOp(tokenType: Token.Type, private val op: Int) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(Type.Primitive.LONG) && right.isAssignableTo(Type.Primitive.LONG)) {
                return getCommonSupertype(left, right)
            }
            throw AmaltheaException("Cannot perform ${this.tokenType} on $left and $right", mutableListOf())
        }

        override fun compile(mv: DeferredMethodVisitor, left: Type, right: Type) {
            mv.visitInsn(getCommonSupertype(left, right).asmType.getOpcode(op))
        }
    }

    data object ShiftLeft : BitOp(DOUBLE_LESS, ISHL)
    data object ShiftRight : BitOp(DOUBLE_GREATER, ISHR)
    data object UShiftRight : BitOp(TRIPLE_GREATER, IUSHR)
    data object BitAnd : BitOp(AMPERSAND, IAND)
    data object BitOr : BitOp(PIPE, IOR)
    data object BitXor : BitOp(CARET, IXOR)

    sealed class EqCmpOp(tokenType: Token.Type) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(right) || right.isAssignableTo(left)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot perform ${this.tokenType} on $left and $right", mutableListOf())
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
            throw AmaltheaException("Cannot perform ${this.tokenType} on $left and $right", mutableListOf())
        }
    }

    data object And : AndOrOp(DOUBLE_AMPERSAND)
    data object Or : AndOrOp(DOUBLE_PIPE)
}