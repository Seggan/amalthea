package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Span
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.inContext
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class TypeChecker private constructor(private val signature: Signature, private val queryEngine: QueryEngine) {

    private val scopes = ArrayDeque<Scope>()
    private var currentScope = 0

    private fun check(node: AstNode.FunctionDeclaration<Unit>): AstNode.FunctionDeclaration<TypeData> {
        val params = node.parameters.map { (name, type) -> name to checkType(type) }
        val paramVars = mutableListOf<LocalVariable>()
        for ((name, type) in params) {
            if (paramVars.any { it.name == name }) {
                throw AmaltheaException("Duplicate parameter name '$name'", node.span)
            }
            paramVars.add(LocalVariable(name, type.extra.type, false, 0))
        }
        scopes.add(Scope(paramVars.toMutableSet(), mutableSetOf()))
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
        scopes.add(Scope(mutableSetOf(), mutableSetOf()))
        currentScope++
        val statements = node.statements.map(::checkStatement)
        scopes.removeFirst()
        currentScope--
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
        if (scopes.any { scope -> scope.allVariables.any { it.name == node.name } }) {
            throw AmaltheaException("Duplicate variable name '${node.name}'", node.span)
        }
        val variable = LocalVariable(node.name, inferredType, node.isMutable, currentScope)
        if (expr != null) {
            scopes.first().initialized.add(variable)
        } else {
            scopes.first().uninitialized.add(variable)
        }
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
        val (variable, scope) = findVariable(node.name, node.span)
        if (!(variable.isMutable || variable in scope.uninitialized)) {
            throw AmaltheaException("Cannot assign to immutable variable '${node.name}'", node.span)
        }
        if (!expr.extra.type.isAssignableTo(variable.type)) {
            throw TypeMismatchException(variable.type, expr.extra.type, node.span)
        }
        scope.initialized.add(variable)
        scope.uninitialized.remove(variable)
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
        val variable = findVariable(node.name, node.span).first
        if (variable in scopes.first().uninitialized) {
            throw AmaltheaException("Variable '${node.name}' is not initialized", node.span)
        }
        return AstNode.Variable(node.name, node.span, TypeData.Variable(variable))
    }

    private fun checkType(node: AstNode.Type<Unit>): AstNode.Type<TypeData> {
        return AstNode.Type(
            node.name,
            node.span,
            TypeData.Basic(inContext(node) { queryEngine[Key.ResolveType(node.name, node.span.source)] })
        )
    }

    private fun findVariable(name: String, span: Span): Pair<LocalVariable, Scope> {
        return scopes.firstNotNullOfOrNull { scope ->
            val variable = scope.allVariables.find { it.name == name }
            if (variable != null) variable to scope else null
        } ?: throw AmaltheaException("Variable '$name' is not defined", span)
    }

    class QueryProvider(private val queryEngine: QueryEngine) :
        Queryable<Key.TypeCheck, AstNode.FunctionDeclaration<TypeData>> {
        override val keyType = Key.TypeCheck::class

        override fun query(key: Key.TypeCheck): AstNode.FunctionDeclaration<TypeData> {
            val untypedAst = queryEngine[Key.FindFunctionBody(key.signature)]
            return TypeChecker(key.signature, queryEngine).check(untypedAst)
        }
    }
}

private data class Scope(val initialized: MutableSet<LocalVariable>, val uninitialized: MutableSet<LocalVariable>) {
    val allVariables: Set<LocalVariable>
        get() = initialized + uninitialized
}

private class TypeMismatchException(expected: Type, actual: Type, span: Span) :
    AmaltheaException("Type mismatch: expected $expected, got $actual", span)