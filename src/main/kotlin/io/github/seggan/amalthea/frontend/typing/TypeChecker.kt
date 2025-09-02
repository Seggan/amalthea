package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.inContext
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeChecker private constructor(private val signature: Signature, private val queryEngine: QueryEngine) {

    private fun check(node: AstNode.FunctionDeclaration<Unit>): AstNode.FunctionDeclaration<TypeData> {
        return AstNode.FunctionDeclaration(
            node.name,
            node.parameters.map { (name, type) -> name to checkType(type) },
            checkType(node.returnType),
            checkBlock(node.body),
            node.span,
            TypeData.Basic(signature.type)
        )
    }

    private fun checkBlock(node: AstNode.Block<Unit>): AstNode.Block<TypeData> {
        return AstNode.Block(node.statements.map(::checkStatement), node.span, TypeData.None)
    }

    private fun checkStatement(node: AstNode.Statement<Unit>): AstNode.Statement<TypeData> = when (node) {
        is AstNode.Expression -> checkExpression(node)
        is AstNode.Block -> checkBlock(node)
        is AstNode.Return -> checkReturn(node)
    }

    private fun checkReturn(node: AstNode.Return<Unit>): AstNode.Return<TypeData> {
        val returnType = signature.type.returnType
        val expr = if (node.expr == null) {
            if (returnType != Type.Unit) {
                throw AmaltheaException("Return type mismatch: expected $returnType, got amalthea::Unit", node.span)
            }
            null
        } else {
            val expr = checkExpression(node.expr)
            if (!expr.extra.type.isAssignableTo(returnType)) {
                throw AmaltheaException("Return type mismatch: expected $returnType, got ${expr.extra.type}", node.span)
            }
            expr
        }
        return AstNode.Return(expr, node.span, TypeData.None)
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
        val function = inContext(node) {
            queryEngine[Key.ResolveFunctionCall(node.name, arguments.map { it.extra.type })]
        }
        return AstNode.FunctionCall(node.name, arguments, node.span, TypeData.FunctionCall(function))
    }

    private fun checkIntLiteral(node: AstNode.IntLiteral<Unit>): AstNode.IntLiteral<TypeData> {
        val type = if (node.value in Int.MIN_VALUE..Int.MAX_VALUE) Type.Primitive.INT else Type.Primitive.LONG
        return AstNode.IntLiteral(node.value, node.span, TypeData.Basic(type))
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
            TypeData.Basic(inContext(node) { queryEngine[Key.ResolveType(node.name)] })
        )
    }

    class QueryProvider(private val queryEngine: QueryEngine) :
        Queryable<Key.TypeCheck, AstNode.FunctionDeclaration<TypeData>> {
        override val keyType = Key.TypeCheck::class

        override fun query(key: Key.TypeCheck): AstNode.FunctionDeclaration<TypeData> {
            val (signature, untypedAst) = queryEngine[Key.ResolveHeader(key.name, key.type)]
            return TypeChecker(signature, queryEngine).check(untypedAst)
        }
    }
}