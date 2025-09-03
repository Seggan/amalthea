package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.parsing.AstNode

class VariableChecker private constructor(private val initialized: MutableSet<LocalVariable>) {

    fun check(block: AstNode.Block<TypeData>) {
        for (statement in block.statements) {
            checkStatement(statement)
        }
    }

    private fun checkStatement(node: AstNode.Statement<TypeData>) {
        when (node) {
            is AstNode.Expression -> checkExpression(node)
            is AstNode.Block -> initialized += checkBlock(node)
            is AstNode.Return -> node.expr?.let(::checkExpression)
            is AstNode.VariableAssignment -> {
                checkExpression(node.expr)
                val variable = (node.extra as TypeData.Variable).variable
                if (variable in initialized && !variable.isMutable) {
                    throw AmaltheaException("Cannot assign to immutable variable '${variable.name}'", node.span)
                }
                initialized += variable
            }

            is AstNode.VariableDeclaration -> {
                if (node.expr != null) {
                    checkExpression(node.expr)
                    initialized += (node.extra as TypeData.Variable).variable
                }
            }
        }
    }

    private fun checkBlock(node: AstNode.Block<TypeData>): Set<LocalVariable> {
        val checker = VariableChecker(initialized.toMutableSet())
        checker.check(node)
        return checker.initialized
    }

    private fun checkExpression(node: AstNode.Expression<TypeData>) {
        when (node) {
            is AstNode.BinaryOp -> {
                checkExpression(node.left)
                checkExpression(node.right)
            }

            is AstNode.BooleanLiteral -> {}
            is AstNode.FloatLiteral -> {}
            is AstNode.FunctionCall -> node.arguments.forEach(::checkExpression)
            is AstNode.IntLiteral -> {}
            is AstNode.StringLiteral -> {}
            is AstNode.UnaryOp -> checkExpression(node.expr)

            is AstNode.Variable -> {
                val variable = (node.extra as TypeData.Variable).variable
                if (variable !in initialized) {
                    throw AmaltheaException("Variable '${variable.name}' might be uninitialized", node.span)
                }
            }
        }
    }

    companion object {
        fun check(block: AstNode.Block<TypeData>) {
            VariableChecker(mutableSetOf()).check(block)
        }
    }
}