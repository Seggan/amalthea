package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.inContext
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeChecker(private val queryEngine: QueryEngine) :
    Queryable<Key.TypeCheck, AstNode.FunctionDeclaration<TypeData>> {
    override val keyType = Key.TypeCheck::class

    override fun query(key: Key.TypeCheck): AstNode.FunctionDeclaration<TypeData> {
        val (signature, untypedAst) = queryEngine[Key.ResolveHeader(
            key.name,
            key.type,
            key.context
        )]
        val body = checkBlock(untypedAst.body)
        return AstNode.FunctionDeclaration(
            untypedAst.name,
            untypedAst.parameters.map { (name, type) -> name to checkType(type) },
            checkType(untypedAst.returnType),
            body,
            untypedAst.span,
            TypeData.Basic(signature.type)
        )
    }

    private fun checkBlock(node: AstNode.Block<Unit>): AstNode.Block<TypeData> {
        return AstNode.Block(node.statements.map(::checkStatement), node.span, TypeData.None)
    }

    private fun checkStatement(node: AstNode.Statement<Unit>): AstNode.Statement<TypeData> = when (node) {
        is AstNode.Expression -> checkExpression(node)
        is AstNode.Block -> checkBlock(node)
    }

    private fun checkExpression(node: AstNode.Expression<Unit>): AstNode.Expression<TypeData> = when (node) {
        is AstNode.BinaryOp -> checkBinaryOp(node)
        is AstNode.FunctionCall -> checkFunctionCall(node)
        is AstNode.IntLiteral -> checkIntLiteral(node)
        is AstNode.FloatLiteral -> checkFloatLiteral(node)
        is AstNode.StringLiteral -> checkStringLiteral(node)
        is AstNode.UnaryOp -> checkUnaryOp(node)
    }

    private fun checkBinaryOp(node: AstNode.BinaryOp<Unit>): AstNode.BinaryOp<TypeData> {
        val left = checkExpression(node.left)
        val right = checkExpression(node.right)
        val resultType = inContext(node) { node.op.checkType(left.extra.type, right.extra.type) }
        return AstNode.BinaryOp(left, node.op, right, TypeData.Basic(resultType))
    }

    private fun checkFunctionCall(node: AstNode.FunctionCall<Unit>): AstNode.FunctionCall<TypeData> {
        val arguments = node.arguments.map(::checkExpression)
        val signature = Signature(
            node.name,
            Type.Function(arguments.map { it.extra.type }, Type.Nothing) // Nothing will unify with everything
        )
        val function = inContext(node) { queryEngine[Key.ResolveFunction(signature, node.span.source.name)] }
        return AstNode.FunctionCall(node.name, arguments, node.span, TypeData.FunctionCall(function))
    }

    private fun checkIntLiteral(node: AstNode.IntLiteral<Unit>): AstNode.IntLiteral<TypeData> {
        return AstNode.IntLiteral(node.value, node.span, TypeData.Basic(Type.Primitive.LONG))
    }

    private fun checkFloatLiteral(node: AstNode.FloatLiteral<Unit>): AstNode.FloatLiteral<TypeData> {
        return AstNode.FloatLiteral(node.value, node.span, TypeData.Basic(Type.Primitive.DOUBLE))
    }

    private fun checkStringLiteral(node: AstNode.StringLiteral<Unit>): AstNode.StringLiteral<TypeData> {
        return AstNode.StringLiteral(node.value, node.span, TypeData.Basic(Type.Primitive.STRING))
    }

    private fun checkUnaryOp(node: AstNode.UnaryOp<Unit>): AstNode.UnaryOp<TypeData> {
        val value = checkExpression(node.expr)
        val resultType = inContext(node) { node.op.checkType(value.extra.type) }
        return AstNode.UnaryOp(node.op, value, node.span, TypeData.Basic(resultType))
    }

    private fun checkType(node: AstNode.Type<Unit>): AstNode.Type<TypeData> {
        return AstNode.Type(
            node.name,
            node.span,
            TypeData.Basic(inContext(node) { queryEngine[Key.ResolveType(node.name, node.span.source.name)] })
        )
    }
}