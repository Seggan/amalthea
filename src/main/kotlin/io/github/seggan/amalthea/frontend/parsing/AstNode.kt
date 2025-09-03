package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.*

sealed interface AstNode<out E> {
    val span: Span
    val extra: E

    data class File<E>(
        val pkg: List<String>,
        val imports: List<Import<E>>,
        val declarations: List<FunctionDeclaration<E>>,
        override val span: Span,
        override val extra: E
    ) : AstNode<E> {
        override fun toString() = buildString {
            appendLine("File:")
            appendIndented("Package: ${pkg.joinToString("::")}")
            appendIndented("Imports:")
            for (imp in imports) {
                appendIndented(imp, indent = 4)
            }
            appendIndented("Functions:")
            for (decl in declarations) {
                appendIndented(decl, indent = 4)
            }
            appendIndented("Extra: $extra")
        }
    }

    data class Import<E>(
        val name: QualifiedName,
        override val span: Span,
        override val extra: E
    ) : AstNode<E> {
        override fun toString() = buildString {
            appendLine("Import:")
            appendIndented("Name: $name")
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

    data class VariableDeclaration<E>(
        val isMutable: Boolean,
        val name: String,
        val type: Type<E>?,
        val expr: Expression<E>?,
        override val span: Span,
        override val extra: E
    ) : Statement<E> {
        override fun toString() = buildString {
            appendLine("VariableDeclaration:")
            appendIndented("Mutable: $isMutable")
            appendIndented("Name: $name")
            if (type != null) {
                appendIndented("Type: $type")
            } else {
                appendIndented("Type: <inferred>")
            }
            if (expr != null) {
                appendIndented("Expression:")
                appendIndented(expr, indent = 4)
            } else {
                appendIndented("Expression: <none>")
            }
            appendIndented("Extra: $extra")
        }
    }

    data class VariableAssignment<E>(
        val name: String,
        val expr: Expression<E>,
        override val span: Span,
        override val extra: E
    ) : Statement<E> {
        override fun toString() = buildString {
            appendLine("VariableAssignment:")
            appendIndented("Name: $name")
            appendIndented("Expression:")
            appendIndented(expr, indent = 4)
            appendIndented("Extra: $extra")
        }
    }

    data class Return<E>(
        val expr: Expression<E>?,
        override val span: Span,
        override val extra: E
    ) : Statement<E> {
        override fun toString() = buildString {
            appendLine("Return:")
            if (expr != null) {
                appendIndented("Expression:")
                appendIndented(expr, indent = 4)
            } else {
                appendIndented("Expression: <none>")
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

    data class IntLiteral<E>(val value: Long, override val span: Span, override val extra: E) : Expression<E> {
        override fun toString() = buildString {
            appendLine("IntLiteral:")
            appendIndented("Value: $value")
            appendIndented("Extra: $extra")
        }
    }

    data class FloatLiteral<E>(val value: Double, override val span: Span, override val extra: E) : Expression<E> {
        override fun toString() = buildString {
            appendLine("FloatLiteral:")
            appendIndented("Value: $value")
            appendIndented("Extra: $extra")
        }
    }

    data class BooleanLiteral<E>(val value: Boolean, override val span: Span, override val extra: E) : Expression<E> {
        override fun toString() = buildString {
            appendLine("BooleanLiteral:")
            appendIndented("Value: $value")
            appendIndented("Extra: $extra")
        }
    }

    data class FunctionCall<E>(
        val name: QualifiedName,
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

    data class Variable<E>(val name: String, override val span: Span, override val extra: E) : Expression<E> {
        override fun toString() = buildString {
            appendLine("VariableReference:")
            appendIndented("Name: $name")
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

inline fun <T> inContext(node: AstNode<*>, block: () -> T): T {
    try {
        return block()
    } catch (e: AmaltheaException) {
        e.addStackFrame(node.span)
        throw e
    }
}