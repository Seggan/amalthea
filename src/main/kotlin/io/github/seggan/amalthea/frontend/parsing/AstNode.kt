package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.BinOp
import io.github.seggan.amalthea.frontend.Span
import io.github.seggan.amalthea.frontend.UnOp
import java.math.BigDecimal

sealed interface AstNode<out E> {
    val span: Span
    val extra: E

    data class File<E>(
        val declarations: List<FunctionDeclaration<E>>,
        override val span: Span,
        override val extra: E
    ) : AstNode<E> {
        override fun toString() = buildString {
            appendLine("File:")
            appendIndented("Functions:")
            for (decl in declarations) {
                appendIndented(decl, indent = 4)
            }
            appendIndented("Extra: $extra")
        }
    }

    data class FunctionDeclaration<E>(
        val name: String,
        val parameters: List<Pair<String, Type<E>>>,
        val returnType: Type<E>,
        val body: Block<E>,
        override val span: Span,
        override val extra: E
    ) : AstNode<E> {
        override fun toString() = buildString {
            appendLine("FunctionDeclaration:")
            appendIndented("Name: $name")
            appendIndented("Parameters:")
            for ((paramName, paramType) in parameters) {
                appendIndented("$paramName: $paramType", indent = 4)
            }
            appendIndented("Return type: $returnType")
            appendIndented("Body:")
            appendIndented(body, indent = 4)
            appendIndented("Extra: $extra")
        }
    }

    sealed interface Statement<out E> : AstNode<E>

    data class Block<E>(
        val statements: List<Statement<E>>,
        override val span: Span,
        override val extra: E
    ) : Statement<E> {
        override fun toString() = buildString {
            appendLine("Block:")
            appendIndented("Statements:")
            for (stmt in statements) {
                appendIndented(stmt, indent = 4)
            }
            appendIndented("Extra: $extra")
        }
    }

    sealed interface Expression<out E> : Statement<E>

    data class StringLiteral<E>(val value: String, override val span: Span, override val extra: E) : Expression<E> {
        override fun toString() = buildString {
            appendLine("StringLiteral:")
            appendIndented("Value: \"$value\"")
            appendIndented("Extra: $extra")
        }
    }

    data class NumberLiteral<E>(val value: BigDecimal, override val span: Span, override val extra: E) : Expression<E> {
        override fun toString() = buildString {
            appendLine("NumberLiteral:")
            appendIndented("Value: $value")
            appendIndented("Extra: $extra")
        }
    }

    data class FunctionCall<E>(
        val name: String,
        val arguments: List<Expression<E>>,
        override val span: Span,
        override val extra: E
    ) : Expression<E> {
        override fun toString() = buildString {
            appendLine("FunctionCall:")
            appendIndented("Name: $name")
            appendIndented("Arguments:")
            for (arg in arguments) {
                appendIndented(arg, indent = 4)
            }
            appendIndented("Extra: $extra")
        }
    }

    data class BinaryOp<E>(
        val left: Expression<E>,
        val op: BinOp,
        val right: Expression<E>,
        override val extra: E
    ) : Expression<E> {
        override val span = left.span + right.span

        override fun toString() = buildString {
            appendLine("BinaryOp:")
            appendIndented("Left:")
            appendIndented(left, indent = 4)
            appendIndented("Operator: $op")
            appendIndented("Right:")
            appendIndented(right, indent = 4)
            appendIndented("Extra: $extra")
        }
    }

    data class UnaryOp<E>(
        val op: UnOp,
        val expr: Expression<E>,
        override val span: Span,
        override val extra: E
    ) : Expression<E> {
        override fun toString() = buildString {
            appendLine("UnaryOp:")
            appendIndented("Operator: $op")
            appendIndented("Expression:")
            appendIndented(expr, indent = 4)
            appendIndented("Extra: $extra")
        }
    }

    data class Type<E>(val name: TypeName, override val span: Span, override val extra: E) : AstNode<E> {
        override fun toString() = name.toString()
    }
}

private fun StringBuilder.appendIndented(obj: Any, indent: Int = 2) {
    val indent = " ".repeat(indent)
    for (line in obj.toString().trim().lines()) {
        append(indent).appendLine(line)
    }
}