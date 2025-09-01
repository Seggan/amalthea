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
import org.objectweb.asm.Opcodes

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
            mv.visitInsn(Opcodes.RETURN)
        } else {
            mv.visitInsn(returnType.asmType.getOpcode(Opcodes.IRETURN))
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
        is AstNode.BinaryOp -> TODO()
        is AstNode.FloatLiteral -> TODO()
        is AstNode.FunctionCall -> compileFunctionCall(node)
        is AstNode.IntLiteral -> TODO()
        is AstNode.StringLiteral -> TODO()
        is AstNode.UnaryOp -> TODO()
    }

    private fun compileFunctionCall(node: AstNode.FunctionCall<TypeData>) {
        val callData = node.extra as TypeData.FunctionCall
        for (intrinsic in Intrinsics.entries) {
            if (intrinsic.signature == callData.signature) {
                intrinsic.compile(mv)
                return
            }
        }
        val (name, type) = callData.signature
        val compiledFunction = queryEngine[Key.Compile(
            name,
            type.toTypeName(),
            TODO()
        )]
    }

    class QueryProvider(private val queryEngine: QueryEngine) : Queryable<Key.Compile, CompiledFunction> {
        override val keyType = Key.Compile::class

        override fun query(key: Key.Compile): CompiledFunction {
            val typedAst = queryEngine[Key.TypeCheck(key.name, key.type, key.context)]
            val (header, _) = queryEngine[Key.ResolveHeader(key.name, key.type, key.context)]
            val mv = DeferredMethodVisitor()
            val compiler = FunctionCompiler(header, queryEngine, mv)
            compiler.compile(typedAst)
            return CompiledFunction(header, mv, compiler.dependencies, key.context)
        }
    }
}