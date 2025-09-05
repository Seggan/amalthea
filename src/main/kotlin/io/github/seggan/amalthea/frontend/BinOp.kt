package io.github.seggan.amalthea.frontend

import io.github.seggan.amalthea.backend.*
import io.github.seggan.amalthea.backend.function.FunctionCompiler
import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.lexing.Token.Type.*
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.TypeData
import io.github.seggan.amalthea.frontend.typing.getCommonSupertype
import io.github.seggan.amalthea.frontend.typing.resolvedType
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*

sealed class BinOp(val tokenType: Token.Type) {

    abstract fun checkType(left: Type, right: Type): Type

    abstract fun compile(
        compiler: FunctionCompiler,
        left: AstNode.Expression<TypeData>,
        right: AstNode.Expression<TypeData>
    )

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

        override fun compile(
            compiler: FunctionCompiler,
            left: AstNode.Expression<TypeData>,
            right: AstNode.Expression<TypeData>
        ) {
            compiler.compileExpression(left)
            compiler.compileExpression(right)
            val supertype = getCommonSupertype(left.resolvedType, right.resolvedType).promoteSmallToInt()
            compiler.mv.visitInsn(supertype.asmType.getOpcode(op))
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

        override fun compile(
            compiler: FunctionCompiler,
            left: AstNode.Expression<TypeData>,
            right: AstNode.Expression<TypeData>
        ) {
            compiler.compileExpression(left)
            compiler.compileExpression(right)
            val supertype = getCommonSupertype(left.resolvedType, right.resolvedType)
            compiler.mv.visitInsn(supertype.asmType.getOpcode(op))
        }
    }

    data object ShiftLeft : BitOp(DOUBLE_LESS, ISHL)
    data object ShiftRight : BitOp(DOUBLE_GREATER, ISHR)
    data object UShiftRight : BitOp(TRIPLE_GREATER, IUSHR)
    data object BitAnd : BitOp(AMPERSAND, IAND)
    data object BitOr : BitOp(PIPE, IOR)
    data object BitXor : BitOp(CARET, IXOR)

    sealed class EqOp(tokenType: Token.Type, val equals: Boolean, val reference: Boolean) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(right) || right.isAssignableTo(left)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot perform ${this.tokenType} on $left and $right", mutableListOf())
        }

        override fun compile(
            compiler: FunctionCompiler,
            left: AstNode.Expression<TypeData>,
            right: AstNode.Expression<TypeData>
        ) {
            val mv = compiler.mv
            val leftType = left.resolvedType.promoteSmallToInt()
            val rightType = right.resolvedType.promoteSmallToInt()
            val common = getCommonSupertype(leftType, rightType)
            val jump = Label()
            val end = Label()
            when (common) {
                Type.Primitive.INT, Type.Primitive.BOOLEAN, Type.Primitive.CHAR -> {
                    compiler.compileExpression(left)
                    compiler.compileExpression(right)
                    mv.visitJumpInsn(if (equals) IF_ICMPEQ else IF_ICMPNE, jump)
                }

                Type.Primitive.LONG -> {
                    compiler.compileExpression(left)
                    if (leftType != Type.Primitive.LONG) mv.visitInsn(I2L)
                    compiler.compileExpression(right)
                    if (rightType != Type.Primitive.LONG) mv.visitInsn(I2L)
                    mv.visitInsn(LCMP)
                    mv.visitJumpInsn(if (equals) IFEQ else IFNE, jump)
                }

                Type.Primitive.FLOAT -> {
                    compiler.compileExpression(left)
                    compiler.compileExpression(right)
                    mv.visitInsn(FCMPL)
                    mv.visitJumpInsn(if (equals) IFEQ else IFNE, jump)
                }

                Type.Primitive.DOUBLE -> {
                    compiler.compileExpression(left)
                    if (leftType != Type.Primitive.DOUBLE) mv.visitInsn(F2D)
                    compiler.compileExpression(right)
                    if (rightType != Type.Primitive.DOUBLE) mv.visitInsn(F2D)
                    mv.visitInsn(DCMPL)
                    mv.visitJumpInsn(if (equals) IFEQ else IFNE, jump)
                }

                else -> {
                    compiler.compileExpression(left)
                    mv.boxConditional(leftType, common)
                    compiler.compileExpression(right)
                    mv.boxConditional(rightType, common)
                    if (reference) {
                        mv.visitJumpInsn(if (equals) IF_ACMPEQ else IF_ACMPNE, jump)
                    } else {
                        mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            asmTypeName<Any>(),
                            "equals",
                            AsmType.getMethodDescriptor(AsmType.BOOLEAN_TYPE, asmType<Any>()),
                            false
                        )
                        mv.visitJumpInsn(if (equals) IFNE else IFEQ, jump)
                    }
                }
            }
            mv.visitInsn(ICONST_0)
            mv.visitJumpInsn(GOTO, end)
            mv.visitLabel(jump)
            mv.visitInsn(ICONST_1)
            mv.visitLabel(end)
        }
    }

    data object Eq : EqOp(DOUBLE_EQUAL, true, false)
    data object NotEq : EqOp(NOT_DOUBLE_EQUAL, false, false)
    data object RefEq : EqOp(TRIPLE_EQUAL, true, true)
    data object RefNotEq : EqOp(NOT_TRIPLE_EQUAL, false, true)

    sealed class CmpOp(tokenType: Token.Type) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(right) || right.isAssignableTo(left)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot perform ${this.tokenType} on $left and $right", mutableListOf())
        }

        override fun compile(
            compiler: FunctionCompiler,
            left: AstNode.Expression<TypeData>,
            right: AstNode.Expression<TypeData>
        ) {
            val mv = compiler.mv
            val leftType = left.resolvedType.promoteSmallToInt()
            val rightType = right.resolvedType.promoteSmallToInt()
            val common = getCommonSupertype(leftType, rightType)
            val jump = Label()
            val end = Label()
            val finalOp = when (this) {
                is Lt -> IFLT
                is LtEq -> IFLE
                is Gt -> IFGT
                is GtEq -> IFGE
            }
            when (common) {
                Type.Primitive.INT, Type.Primitive.BOOLEAN, Type.Primitive.CHAR -> {
                    compiler.compileExpression(left)
                    compiler.compileExpression(right)
                    val intOp = when (this) {
                        is Lt -> IF_ICMPLT
                        is LtEq -> IF_ICMPLE
                        is Gt -> IF_ICMPGT
                        is GtEq -> IF_ICMPGE
                    }
                    mv.visitJumpInsn(intOp, jump)
                }

                Type.Primitive.LONG -> {
                    compiler.compileExpression(left)
                    if (leftType != Type.Primitive.LONG) mv.visitInsn(I2L)
                    compiler.compileExpression(right)
                    if (rightType != Type.Primitive.LONG) mv.visitInsn(I2L)
                    mv.visitInsn(LCMP)
                    mv.visitJumpInsn(finalOp, jump)
                }

                Type.Primitive.FLOAT -> {
                    compiler.compileExpression(left)
                    compiler.compileExpression(right)
                    mv.visitInsn(
                        when (this) {
                            is Lt, is LtEq -> FCMPL
                            is Gt, is GtEq -> FCMPG
                        }
                    )
                    mv.visitJumpInsn(finalOp, jump)
                }

                Type.Primitive.DOUBLE -> {
                    compiler.compileExpression(left)
                    if (leftType != Type.Primitive.DOUBLE) mv.visitInsn(F2D)
                    compiler.compileExpression(right)
                    if (rightType != Type.Primitive.DOUBLE) mv.visitInsn(F2D)
                    mv.visitInsn(
                        when (this) {
                            is Lt, is LtEq -> DCMPL
                            is Gt, is GtEq -> DCMPG
                        }
                    )
                    mv.visitJumpInsn(finalOp, jump)
                }

                else -> {
                    TODO()
                }
            }
            mv.visitInsn(ICONST_0)
            mv.visitJumpInsn(GOTO, end)
            mv.visitLabel(jump)
            mv.visitInsn(ICONST_1)
            mv.visitLabel(end)
        }
    }

    data object Lt : CmpOp(LESS)
    data object LtEq : CmpOp(LESS_EQUAL)
    data object Gt : CmpOp(GREATER)
    data object GtEq : CmpOp(GREATER_EQUAL)

    sealed class AndOrOp(tokenType: Token.Type) : BinOp(tokenType) {
        override fun checkType(left: Type, right: Type): Type {
            if (left.isAssignableTo(Type.Primitive.BOOLEAN) && right.isAssignableTo(Type.Primitive.BOOLEAN)) {
                return Type.Primitive.BOOLEAN
            }
            throw AmaltheaException("Cannot perform ${this.tokenType} on $left and $right", mutableListOf())
        }
    }

    data object And : AndOrOp(DOUBLE_AMPERSAND) {
        override fun compile(
            compiler: FunctionCompiler,
            left: AstNode.Expression<TypeData>,
            right: AstNode.Expression<TypeData>
        ) {
            val mv = compiler.mv
            val isFalse = Label()
            val end = Label()
            compiler.compileExpression(left)
            mv.visitJumpInsn(IFEQ, isFalse)
            compiler.compileExpression(right)
            mv.visitJumpInsn(IFEQ, isFalse)
            mv.visitInsn(ICONST_1)
            mv.visitJumpInsn(GOTO, end)
            mv.visitLabel(isFalse)
            mv.visitInsn(ICONST_0)
            mv.visitLabel(end)
        }
    }

    data object Or : AndOrOp(DOUBLE_PIPE) {
        override fun compile(
            compiler: FunctionCompiler,
            left: AstNode.Expression<TypeData>,
            right: AstNode.Expression<TypeData>
        ) {
            val mv = compiler.mv
            val isTrue = Label()
            val end = Label()
            compiler.compileExpression(left)
            mv.visitJumpInsn(IFNE, isTrue)
            compiler.compileExpression(right)
            mv.visitJumpInsn(IFNE, isTrue)
            mv.visitInsn(ICONST_0)
            mv.visitJumpInsn(GOTO, end)
            mv.visitLabel(isTrue)
            mv.visitInsn(ICONST_1)
            mv.visitLabel(end)
        }
    }
}