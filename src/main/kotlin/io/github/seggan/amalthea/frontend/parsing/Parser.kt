package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.*
import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.lexing.Token.Type.*
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class Parser private constructor(private val tokens: List<Token>) {

    private val errors = mutableListOf<ParseException>()

    private var index = 0
    private val isEof: Boolean
        get() = index >= tokens.size

    private fun parse(): AstNode.File<Unit> {
        val pkg = parsePackage()
        val imports = mutableListOf<AstNode.Import<Unit>>()
        while (true) {
            imports.add(parseImport() ?: break)
        }
        val structs = mutableListOf<AstNode.Struct<Unit>>()
        val functions = mutableListOf<AstNode.Function<Unit>>()
        while (index < tokens.size) {
            if (tryConsume(STRUCT) != null) {
                index--
                structs.add(parseStruct())
                continue
            }
            if (tryConsume(FUN) != null) {
                index--
                functions.add(parseFunction())
                continue
            }
        }
        val span = if (tokens.isEmpty()) {
            Span(0, 0, CodeSource.constant("", ""))
        } else {
            tokens.first().span + tokens.last().span
        }
        return AstNode.File(pkg, imports, structs, functions, span, Unit)
    }

    private fun parsePackage(): List<String> {
        val pkg = mutableListOf<String>()
        consume(PACKAGE)
        while (true) {
            val id = parseId()
            pkg.add(id.text)
            if (tryConsume(SEMICOLON) != null) {
                break
            }
            consume(DOUBLE_COLON)
        }
        return pkg
    }

    private fun parseImport(): AstNode.Import<Unit>? {
        val start = tryConsume(IMPORT)?.span ?: return null
        val (qName, span) = parseQualifiedName()
        val fullSpan = start + span
        consume(SEMICOLON)
        return AstNode.Import(qName, fullSpan, Unit)
    }

    private fun parseStruct(): AstNode.Struct<Unit> {
        val start = consume(STRUCT).span
        val name = parseId().text
        consume(OPEN_BRACE)
        val fields = parseArgList(CLOSE_BRACE) {
            val fieldName = parseId().text
            consume(COLON)
            val fieldType = parseType()
            fieldName to fieldType
        }
        return AstNode.Struct(name, fields, start + tokens[index - 1].span, Unit)
    }

    private fun parseFunction(): AstNode.Function<Unit> {
        val start = consume(FUN).span
        val name = parseId().text
        consume(OPEN_PAREN)
        val parameters = parseArgList(CLOSE_PAREN) {
            val paramName = parseId().text
            consume(COLON)
            val paramType = parseType()
            paramName to paramType
        }
        val returnType = if (tryConsume(COLON) != null) parseType() else AstNode.Type(
            TypeName.Simple(Type.Unit.qName),
            start,
            Unit
        )
        var body = parseBlock()
        if (returnType.name == Type.Unit.asTypeName()) {
            if (body.statements.lastOrNull() !is AstNode.Return) {
                body = AstNode.Block(
                    body.statements + AstNode.Return(null, body.span, Unit),
                    body.span,
                    Unit
                )
            }
        }
        return AstNode.Function(name, parameters, returnType, body, start + body.span, Unit)
    }

    private fun parseBlock(): AstNode.Block<Unit> {
        val start = consume(OPEN_BRACE).span
        val statements = mutableListOf<AstNode.Statement<Unit>>()
        lateinit var lastSpan: Span
        while (true) {
            val closeBrace = tryConsume(CLOSE_BRACE)
            if (closeBrace != null) {
                lastSpan = closeBrace.span
                break
            }
            try {
                statements.add(parseStatement())
            } catch (e: ParseException) {
                errors.add(e)
                skipUntil(SEMICOLON)
                index++
            }
        }
        return AstNode.Block(statements, start + lastSpan, Unit)
    }

    private fun parseStatement(): AstNode.Statement<Unit> = oneOf(
        { parseExpression().also { consume(SEMICOLON) } },
        ::parseBlock,
        ::parseReturn,
        ::parseVariableDeclaration,
        ::parseVariableAssignment,
        ::parseStructMutation,
        ::parseIf,
        ::parseWhile
    )

    private fun parseVariableDeclaration(): AstNode.VariableDeclaration<Unit> {
        val mut = tryConsume(MUT)
        val varTok = consume(VAR)
        val start = mut?.span ?: varTok.span
        val name = parseId().text
        val type = tryConsume(COLON)?.let { parseType() }
        val expr = if (tryConsume(SEMICOLON) != null) {
            null
        } else {
            consume(EQUAL)
            val expr = parseExpression()
            consume(SEMICOLON)
            expr
        }
        val span = start + tokens[index - 1].span
        return AstNode.VariableDeclaration(mut != null, name, type, expr, span, Unit)
    }

    private fun parseVariableAssignment(): AstNode.VariableAssignment<Unit> {
        val name = parseId()
        consume(EQUAL)
        val expr = parseExpression()
        val span = name.span + consume(SEMICOLON).span
        return AstNode.VariableAssignment(name.text, expr, span, Unit)
    }

    private fun parseStructMutation(): AstNode.StructMutation<Unit> {
        val target = parsePrimary()
        consume(DOT)
        val name = parseId().text
        consume(EQUAL)
        val expr = parseExpression()
        val span = target.span + consume(SEMICOLON).span
        return AstNode.StructMutation(target, name, expr, span, Unit)
    }

    private fun parseIf(): AstNode.If<Unit> {
        val start = consume(IF).span
        consume(OPEN_PAREN)
        val condition = parseExpression()
        consume(CLOSE_PAREN)
        val thenBranch = parseBlock()
        val elseBranch = if (tryConsume(ELSE) != null) parseBlock() else null
        val span = if (elseBranch != null) {
            start + elseBranch.span
        } else {
            start + thenBranch.span
        }
        return AstNode.If(condition, thenBranch, elseBranch, span, Unit)
    }

    private fun parseWhile(): AstNode.While<Unit> {
        val start = consume(WHILE).span
        consume(OPEN_PAREN)
        val condition = parseExpression()
        consume(CLOSE_PAREN)
        val body = parseBlock()
        return AstNode.While(condition, body, start + body.span, Unit)
    }

    private fun parseReturn(): AstNode.Return<Unit> {
        val start = consume(RETURN).span
        val expr = if (tryConsume(SEMICOLON) != null) null else parseExpression().also { consume(SEMICOLON) }
        val span = if (expr != null) start + expr.span else start + tokens[index - 1].span
        return AstNode.Return(expr, span, Unit)
    }

    private fun parseExpression() = parseAndOr()

    private fun parseAndOr() = parseBinOp(
        ::parseEquality,
        mapOf(DOUBLE_AMPERSAND to BinOp.And, DOUBLE_PIPE to BinOp.Or)
    )

    private fun parseEquality() = parseBinOp(
        ::parseComparison,
        mapOf(
            DOUBLE_EQUAL to BinOp.Eq,
            NOT_DOUBLE_EQUAL to BinOp.NotEq,
            TRIPLE_EQUAL to BinOp.RefEq,
            NOT_TRIPLE_EQUAL to BinOp.RefNotEq
        )
    )

    private fun parseComparison() = parseBinOp(
        ::parseBitOr,
        mapOf(
            LESS to BinOp.Lt,
            LESS_EQUAL to BinOp.LtEq,
            GREATER to BinOp.Gt,
            GREATER_EQUAL to BinOp.GtEq
        )
    )

    private fun parseBitOr() = parseBinOp(::parseBitXor, mapOf(PIPE to BinOp.BitOr))
    private fun parseBitXor() = parseBinOp(::parseBitAnd, mapOf(CARET to BinOp.BitXor))
    private fun parseBitAnd() = parseBinOp(::parseShift, mapOf(AMPERSAND to BinOp.BitAnd))
    private fun parseShift() = parseBinOp(
        ::parseAddSub,
        mapOf(
            DOUBLE_LESS to BinOp.ShiftLeft,
            DOUBLE_GREATER to BinOp.ShiftRight,
            TRIPLE_GREATER to BinOp.UShiftRight
        )
    )

    private fun parseAddSub() = parseBinOp(
        ::parseMulDiv,
        mapOf(PLUS to BinOp.Add, MINUS to BinOp.Sub)
    )

    private fun parseMulDiv() = parseBinOp(
        ::parseUnary,
        mapOf(STAR to BinOp.Mul, SLASH to BinOp.Div, PERCENT to BinOp.Mod)
    )

    private inline fun parseBinOp(
        next: () -> AstNode.Expression<Unit>,
        types: Map<Token.Type, BinOp>
    ): AstNode.Expression<Unit> {
        var expr = next()
        val keys = types.keys.toTypedArray()
        while (tryConsume(*keys) != null) {
            expr = AstNode.BinaryOp(expr, types[tokens[index - 1].type]!!, next(), Unit)
        }
        return expr
    }

    private fun parseUnary(): AstNode.Expression<Unit> {
        val op = tryConsume(PLUS, MINUS, EXCLAMATION, TILDE)
        if (op != null) {
            val expr = parseUnary()
            return AstNode.UnaryOp(
                when (op.type) {
                    PLUS -> UnOp.PLUS
                    MINUS -> UnOp.NEG
                    EXCLAMATION -> UnOp.NOT
                    TILDE -> UnOp.BIT_NOT
                    else -> throw AssertionError("Unreachable")
                },
                expr,
                op.span + expr.span,
                Unit
            )
        }
        return parseFieldAccess()
    }

    private fun parseFieldAccess(): AstNode.Expression<Unit> {
        var expr = parsePrimary()
        while (tryConsume(DOT) != null) {
            val fieldName = parseId().text
            expr = AstNode.FieldAccess(expr, fieldName, expr.span + tokens[index - 1].span, Unit)
        }
        return expr
    }

    private fun parsePrimary(): AstNode.Expression<Unit> = oneOf(
        ::parseNumber,
        ::parseString,
        ::parseBoolean,
        ::parseStructLiteral,
        ::parseParens,
        ::parseFunctionCall,
        ::parseVariable,
    )

    private fun parseNumber(): AstNode.Expression<Unit> {
        val token = consume(NUMBER)
        return if ('.' in token.text) {
            AstNode.FloatLiteral(token.text.toDouble(), token.span, Unit)
        } else {
            AstNode.IntLiteral(token.text.toLong(), token.span, Unit)
        }
    }

    private fun parseString(): AstNode.StringLiteral<Unit> {
        val token = consume(STRING)
        val str = buildString {
            var i = 1
            while (i < token.text.length - 1) {
                val c = token.text[i]
                if (c == '\\') {
                    i++
                    when (val esc = token.text[i]) {
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        '\\' -> append('\\')
                        '"' -> append('"')
                        'u' -> {
                            val code = token.text.substring(i + 1, i + 5).toInt(16)
                            append(code.toChar())
                            i += 4
                        }

                        else -> throw ParseException("Unknown escape sequence \\$esc", token.span)
                    }
                } else {
                    append(c)
                }
                i++
            }
        }
        return AstNode.StringLiteral(str, token.span, Unit)
    }

    private fun parseBoolean(): AstNode.Expression<Unit> {
        val token = consume(TRUE, FALSE)
        return AstNode.BooleanLiteral(token.type == TRUE, token.span, Unit)
    }

    private fun parseStructLiteral(): AstNode.StructLiteral<Unit> {
        val type = parseType()
        consume(OPEN_BRACE)
        val fields = parseArgList(CLOSE_BRACE) {
            val fieldName = parseId().text
            consume(COLON)
            val fieldValue = parseExpression()
            fieldName to fieldValue
        }
        return AstNode.StructLiteral(type, fields, type.span + tokens[index - 1].span, Unit)
    }

    private fun parseParens(): AstNode.Expression<Unit> {
        consume(OPEN_PAREN)
        val expr = parseExpression()
        consume(CLOSE_PAREN)
        return expr
    }

    private fun parseFunctionCall(): AstNode.FunctionCall<Unit> {
        val (qName, span) = parseQualifiedName()
        consume(OPEN_PAREN)
        val args = parseArgList(CLOSE_PAREN, ::parseExpression)
        return AstNode.FunctionCall(qName, args, span + tokens[index - 1].span, Unit)
    }

    private fun parseVariable(): AstNode.Expression<Unit> {
        val id = parseId()
        return AstNode.Variable(id.text, id.span, Unit)
    }

    private fun parseType(): AstNode.Type<Unit> {
        val (qName, span) = parseQualifiedName()
        return AstNode.Type(TypeName.Simple(qName), span, Unit)
    }

    private fun parseId(): Token {
        return consume(IDENTIFIER) // can add soft keywords
    }

    private fun parseQualifiedName(): Pair<QualifiedName, Span> {
        val parts = mutableListOf<String>()
        var span: Span? = null
        do {
            val id = parseId()
            if (span == null) {
                span = id.span
            } else {
                span += id.span
            }
            parts.add(id.text)
        } while (tryConsume(DOUBLE_COLON) != null)
        return QualifiedName(parts.dropLast(1), parts.last()) to span
    }

    private fun tryConsume(vararg types: Token.Type): Token? {
        if (isEof) return null
        val token = tokens[index]
        if (token.type in types) {
            index++
            return token
        } else if (token.type == ERROR) {
            throw ParseException("Unknown character '${token.text}'", token.span)
        }
        return null
    }

    private fun consume(vararg types: Token.Type): Token {
        val consumed = tryConsume(*types)
        if (consumed != null) {
            return consumed
        }
        if (!isEof) {
            val token = tokens[index]
            throw UnexpectedTokenException(types.toList(), "'${token.text}'", token.span)
        } else {
            val lastToken = tokens.last()
            throw UnexpectedTokenException(types.toList(), "end of file", lastToken.span)
        }
    }

    private fun skipUntil(vararg types: Token.Type, inclusive: Boolean = false) {
        while (!isEof && tokens[index].type !in types) {
            index++
        }
        if (isEof) {
            throw UnexpectedTokenException(types.toList(), "end of file", tokens.last().span)
        }
        if (inclusive) {
            index++
        }
    }

    private fun <T : AstNode<Unit>> oneOf(vararg parsers: () -> T): T {
        val errors = mutableListOf<Pair<UnexpectedTokenException, Int>>()
        val originalIndex = index
        for (parser in parsers) {
            try {
                return parser()
            } catch (e: UnexpectedTokenException) {
                errors.add(e to index)
                index = originalIndex
            }
        }
        val max = errors.maxOf { it.second }
        val maxErrors = errors.filter { it.second == max }.map { it.first }
        throw UnexpectedTokenException(
            maxErrors.flatMap { it.expected }.distinct(),
            if (isEof) "end of file" else "'${tokens[index].text}'",
            if (isEof) tokens.last().span else tokens[index].span
        )
    }

    private inline fun <T> parseArgList(closer: Token.Type, subParser: () -> T): List<T> {
        val args = mutableListOf<T>()
        while (true) {
            if (tryConsume(closer) != null) break
            args.add(subParser())
            if (tryConsume(closer) != null) break
            consume(COMMA)
        }
        return args
    }

    class QueryProvider(private val queryEngine: QueryEngine) : Queryable<Key.UntypedAst, AstNode.File<Unit>> {

        override val keyType = Key.UntypedAst::class

        override fun query(key: Key.UntypedAst): AstNode.File<Unit> {
            val tokens = queryEngine[Key.Tokens(key.source)]
            val parser = Parser(tokens)
            val ast = parser.parse()
            if (parser.errors.isNotEmpty()) {
                if (parser.errors.size == 1) {
                    throw parser.errors.first()
                } else {
                    throw CompositeAmaltheaException(parser.errors)
                }
            }
            return ast
        }
    }
}