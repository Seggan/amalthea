package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Span
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.inContext
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeChecker private constructor(private val signature: Signature, private val queryEngine: QueryEngine) {

    private val scopes = ArrayDeque<MutableSet<LocalVariable>>()
    private var variableId = 0

    private fun check(node: AstNode.FunctionDeclaration<Unit>): AstNode.FunctionDeclaration<TypeData> {
        val params = node.parameters.map { (name, type) -> name to checkType(type) }
        val paramVars = mutableListOf<LocalVariable>()
        for ((name, type) in params) {
            if (paramVars.any { it.name == name }) {
                throw AmaltheaException("Duplicate parameter name '$name'", node.span)
            }
            paramVars.add(LocalVariable(variableId++, name, type.extra.type, false))
        }
        scopes.add(paramVars.toMutableSet())
        val body = checkBlock(node.body)
        scopes.removeFirst()
        return AstNode.FunctionDeclaration(
            node.name,
            params,
            checkType(node.returnType),
            body,
            node.span,
            TypeData.Function(signature, paramVars)
        )
    }

    private fun checkBlock(node: AstNode.Block<Unit>): AstNode.Block<TypeData> {
        scopes.add(mutableSetOf())
        val statements = node.statements.map(::checkStatement)
        scopes.removeFirst()
        return AstNode.Block(statements, node.span, TypeData.None)
    }

    private fun checkStatement(node: AstNode.Statement<Unit>): AstNode.Statement<TypeData> = when (node) {
        is AstNode.Expression -> checkExpression(node)
        is AstNode.Block -> checkBlock(node)
        is AstNode.Return -> checkReturn(node)
        is AstNode.VariableAssignment -> checkVariableAssignment(node)
        is AstNode.VariableDeclaration -> checkVariableDeclaration(node)
    }

    private fun checkVariableDeclaration(node: AstNode.VariableDeclaration<Unit>): AstNode.VariableDeclaration<TypeData> {
        val type = node.type?.let(::checkType)
        val expr = node.expr?.let(::checkExpression)
        if (type != null && expr != null && !expr.extra.type.isAssignableTo(type.extra.type)) {
            throw TypeMismatchException(type.extra.type, expr.extra.type, node.span)
        }
        val inferredType = type?.extra?.type ?: expr?.extra?.type
        ?: throw AmaltheaException("Cannot infer type for variable '${node.name}'", node.span)
        if (scopes.any { scope -> scope.any { it.name == node.name } }) {
            throw AmaltheaException("Duplicate variable name '${node.name}'", node.span)
        }

        val variable = LocalVariable(variableId++, node.name, inferredType, node.isMutable)
        scopes.first().add(variable)
        return AstNode.VariableDeclaration(
            node.isMutable,
            node.name,
            type,
            expr,
            node.span,
            TypeData.Variable(variable)
        )
    }

    private fun checkVariableAssignment(node: AstNode.VariableAssignment<Unit>): AstNode.VariableAssignment<TypeData> {
        val expr = checkExpression(node.expr)
        val variable = findVariable(node.name, node.span)
        if (!expr.extra.type.isAssignableTo(variable.type)) {
            throw TypeMismatchException(variable.type, expr.extra.type, node.span)
        }
        return AstNode.VariableAssignment(node.name, expr, node.span, TypeData.Variable(variable))
    }

    private fun checkReturn(node: AstNode.Return<Unit>): AstNode.Return<TypeData> {
        val returnType = signature.type.returnType
        val expr = if (node.expr == null) {
            if (returnType != Type.Unit) {
                throw TypeMismatchException(Type.Unit, returnType, node.span)
            }
            null
        } else {
            val expr = checkExpression(node.expr)
            if (!expr.extra.type.isAssignableTo(returnType)) {
                throw TypeMismatchException(returnType, expr.extra.type, node.span)
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
        is AstNode.BooleanLiteral -> checkBooleanLiteral(node)
        is AstNode.StringLiteral -> checkStringLiteral(node)
        is AstNode.UnaryOp -> checkUnaryOp(node)
        is AstNode.Variable -> checkVariable(node)
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
            queryEngine[Key.ResolveFunctionCall(node.name, arguments.map { it.extra.type }, node.span.source)]
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

    private fun checkBooleanLiteral(node: AstNode.BooleanLiteral<Unit>): AstNode.BooleanLiteral<TypeData> {
        return AstNode.BooleanLiteral(node.value, node.span, TypeData.Basic(Type.Primitive.BOOLEAN))
    }

    private fun checkUnaryOp(node: AstNode.UnaryOp<Unit>): AstNode.UnaryOp<TypeData> {
        val value = checkExpression(node.expr)
        val resultType = inContext(node) { node.op.checkType(value.extra.type) }
        return AstNode.UnaryOp(node.op, value, node.span, TypeData.Basic(resultType))
    }

    private fun checkVariable(node: AstNode.Variable<Unit>): AstNode.Variable<TypeData> {
        return AstNode.Variable(node.name, node.span, TypeData.Variable(findVariable(node.name, node.span)))
    }

    private fun checkType(node: AstNode.Type<Unit>): AstNode.Type<TypeData> {
        return AstNode.Type(
            node.name,
            node.span,
            TypeData.Basic(inContext(node) { queryEngine[Key.ResolveType(node.name, node.span.source)] })
        )
    }

    private fun findVariable(name: String, span: Span): LocalVariable {
        return scopes.firstNotNullOfOrNull { scope -> scope.find { it.name == name } }
            ?: throw AmaltheaException("Variable '$name' is not defined", span)
    }

    class QueryProvider(private val queryEngine: QueryEngine) :
        Queryable<Key.TypeCheck, AstNode.FunctionDeclaration<TypeData>> {
        override val keyType = Key.TypeCheck::class

        override fun query(key: Key.TypeCheck): AstNode.FunctionDeclaration<TypeData> {
            val function = queryEngine[Key.FindFunctionBody(key.signature)]
            if (!checkReturns(function.body)) {
                throw AmaltheaException("Function ${key.signature} does not return on all paths", function.span)
            }
            val checked = TypeChecker(key.signature, queryEngine).check(function)
            VariableChecker.check(checked.body)
            return checked
        }

        private fun checkReturns(node: AstNode.Statement<*>): Boolean = when (node) {
            is AstNode.Block -> node.statements.any(::checkReturns)
            is AstNode.Expression -> false
            is AstNode.Return -> true
            is AstNode.VariableAssignment -> false
            is AstNode.VariableDeclaration -> false
        }
    }
}

private class TypeMismatchException(expected: Type, actual: Type, span: Span) :
    AmaltheaException("Type mismatch: expected $expected, got $actual", span)