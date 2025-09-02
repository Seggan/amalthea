package io.github.seggan.amalthea.backend.compilation

import io.github.seggan.amalthea.frontend.Intrinsics
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.typing.Signature
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.TypeData
import io.github.seggan.amalthea.frontend.typing.asmType
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable
import org.objectweb.asm.Opcodes.*

class FunctionCompiler private constructor(
    private val header: Signature,
    private val queryEngine: QueryEngine,
    private val mv: DeferredMethodVisitor
) {

    private val dependencies = mutableSetOf<CompiledFunction>()

    private fun compile(ast: AstNode.FunctionDeclaration<TypeData>) {
        compileBlock(ast.body)
        val returnType = header.type.returnType
        if (returnType == Type.Unit || returnType == Type.Nothing) {
            mv.visitInsn(RETURN)
        } else {
            mv.visitInsn(returnType.asmType.getOpcode(IRETURN))
        }
    }

    private fun compileBlock(node: AstNode.Block<TypeData>) {
        for (statement in node.statements) {
            compileStatement(statement)
        }
    }

    private fun compileStatement(node: AstNode.Statement<TypeData>) = when (node) {
        is AstNode.Expression -> compileExpression(node)
        is AstNode.Block -> compileBlock(node)
    }

    private fun compileExpression(node: AstNode.Expression<TypeData>) = when (node) {
        is AstNode.BinaryOp -> compileBinaryOp(node)
        is AstNode.FloatLiteral -> compileFloatLiteral(node)
        is AstNode.FunctionCall -> compileFunctionCall(node)
        is AstNode.IntLiteral -> compileIntLiteral(node)
        is AstNode.StringLiteral -> compileStringLiteral(node)
        is AstNode.UnaryOp -> compileUnaryOp(node)
    }

    private fun compileBinaryOp(node: AstNode.BinaryOp<TypeData>) {
        val leftType = node.left.extra.type
        val rightType = node.right.extra.type
        compileExpression(node.left)
        mv.boxConditional(leftType, rightType)
        compileExpression(node.right)
        mv.boxConditional(rightType, leftType)
        node.op.compile(mv, leftType, rightType)
    }

    private fun compileFloatLiteral(node: AstNode.FloatLiteral<TypeData>) {
        when (node.value) {
            0.0 -> mv.visitInsn(DCONST_0)
            1.0 -> mv.visitInsn(DCONST_1)
            else -> mv.visitLdcInsn(node.value)
        }
        if (node.extra.type == Type.Primitive.FLOAT) {
            mv.visitInsn(D2F)
        }
    }

    private fun compileFunctionCall(node: AstNode.FunctionCall<TypeData>) {
        val callData = node.extra as TypeData.FunctionCall
        val (name, type) = callData.signature

        for ((argNode, argType) in node.arguments.zip(type.args)) {
            compileExpression(argNode)
            mv.boxConditional(argNode.extra.type, argType)
        }

        for (intrinsic in Intrinsics.entries) {
            if (intrinsic.signature == callData.signature) {
                intrinsic.compile(mv)
                return
            }
        }
        val compiledFunction = queryEngine[Key.Compile(name, type.asTypeName())]
        TODO()
    }

    private fun compileIntLiteral(node: AstNode.IntLiteral<TypeData>) {
        val isLong = node.value !in Int.MIN_VALUE..Int.MAX_VALUE
        when (node.value) {
            in -1..5 -> mv.visitInsn(ICONST_0 + node.value.toInt())
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> mv.visitIntInsn(BIPUSH, node.value.toInt())
            in Short.MIN_VALUE..Short.MAX_VALUE -> mv.visitIntInsn(SIPUSH, node.value.toInt())
            in Int.MIN_VALUE..Int.MAX_VALUE -> mv.visitLdcInsn(node.value.toInt())
            else -> mv.visitLdcInsn(node.value)
        }
        if (!isLong) {
            when (node.extra.type) {
                Type.Primitive.BYTE -> mv.visitInsn(I2B)
                Type.Primitive.SHORT -> mv.visitInsn(I2S)
                Type.Primitive.LONG -> mv.visitInsn(I2L)
                else -> {}
            }
        }
    }

    private fun compileStringLiteral(node: AstNode.StringLiteral<TypeData>) {
        mv.visitLdcInsn(node.value)
    }

    private fun compileUnaryOp(node: AstNode.UnaryOp<TypeData>) {
        compileExpression(node.expr)
        node.op.compile(mv, node.expr.extra.type)
    }

    class QueryProvider(private val queryEngine: QueryEngine) : Queryable<Key.Compile, CompiledFunction> {
        override val keyType = Key.Compile::class

        override fun query(key: Key.Compile): CompiledFunction {
            val typedAst = queryEngine[Key.TypeCheck(key.name, key.type)]
            val (header, _) = queryEngine[Key.ResolveHeader(key.name, key.type)]
            val mv = DeferredMethodVisitor()
            val compiler = FunctionCompiler(header, queryEngine, mv)
            compiler.compile(typedAst)
            return CompiledFunction(header, mv, compiler.dependencies)
        }
    }
}

private fun DeferredMethodVisitor.boxConditional(provided: Type, expected: Type) {
    if (!(provided is Type.Primitive && expected !is Type.Primitive)) return
    when (provided) {
        Type.Primitive.BYTE -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Byte",
            "valueOf",
            "(B)Ljava/lang/Byte;",
            false
        )

        Type.Primitive.SHORT -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Short",
            "valueOf",
            "(S)Ljava/lang/Short;",
            false
        )

        Type.Primitive.INT -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Integer",
            "valueOf",
            "(I)Ljava/lang/Integer;",
            false
        )

        Type.Primitive.LONG -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Long",
            "valueOf",
            "(J)Ljava/lang/Long;",
            false
        )

        Type.Primitive.FLOAT -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Float",
            "valueOf",
            "(F)Ljava/lang/Float;",
            false
        )

        Type.Primitive.DOUBLE -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Double",
            "valueOf",
            "(D)Ljava/lang/Double;",
            false
        )

        Type.Primitive.BOOLEAN -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Boolean",
            "valueOf",
            "(Z)Ljava/lang/Boolean;",
            false
        )

        Type.Primitive.CHAR -> visitMethodInsn(
            INVOKESTATIC,
            "java/lang/Character",
            "valueOf",
            "(C)Ljava/lang/Character;",
            false
        )

        else -> {}
    }
}