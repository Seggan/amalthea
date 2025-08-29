package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.*
import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.lexing.Token.Type.*
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class Parser private constructor(private val tokens: List<Token>) {

    private val errors = mutableListOf<ParseException>()

    private var index = 0
    private val isEof: Boolean
        get() = index >= tokens.size

    private fun parse(): AstNode.File<Unit> {
        val functions = mutableListOf<AstNode.FunctionDeclaration<Unit>>()
        while (index < tokens.size) {
            try {
                try {
                    functions.add(parseFunction())
                } catch (e: ParseException) {
                    errors.add(e)
                    skipUntil(FUN)
                }
            } catch (e: ParseException) {
                errors.add(e)
                index++
            }
        }
        val span = if (tokens.isEmpty()) {
            Span(0, 0, CodeSource.constant("", ""))
        } else {
            tokens.first().span + tokens.last().span
        }
        return AstNode.File(functions, span, Unit)
    }

    private fun parseFunction(): AstNode.FunctionDeclaration<Unit> {
        val start = consume(FUN).span
        val name = parseId().text
        consume(OPEN_PAREN)
        val parameters = parseArgList(CLOSE_PAREN) {
            val paramName = parseId().text
            consume(COLON)
            val paramType = parseType()
            paramName to paramType
        }
        val returnType = if (tryConsume(COLON) != null) parseType() else AstNode.Type(TypeName.Simple("Unit"), start, Unit)
        val body = parseBlock()
        return AstNode.FunctionDeclaration(name, parameters, returnType, body, start + body.span, Unit)
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
            }
        }
        return AstNode.Block(statements, start + lastSpan, Unit)
    }

    private fun parseStatement(): AstNode.Statement<Unit> = oneOf(
        { parseExpression().also { consume(SEMICOLON) } },
        ::parseBlock
    )

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
            TRIPLE_EQUAL to BinOp.Is,
            NOT_TRIPLE_EQUAL to BinOp.NotIs
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
        return parsePrimary()
    }

    private fun parsePrimary(): AstNode.Expression<Unit> = oneOf(
        ::parseNumber,
        ::parseString,
        ::parseParens,
        ::parseFunctionCall
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

    private fun parseParens(): AstNode.Expression<Unit> {
        consume(OPEN_PAREN)
        val expr = parseExpression()
        consume(CLOSE_PAREN)
        return expr
    }

    private fun parseFunctionCall(): AstNode.FunctionCall<Unit> {
        val name = parseId()
        consume(OPEN_PAREN)
        val args = parseArgList(CLOSE_PAREN, ::parseExpression)
        return AstNode.FunctionCall(name.text, args, name.span + tokens[index - 1].span, Unit)
    }

    private fun parseType(): AstNode.Type<Unit> {
        val name = parseId()
        return AstNode.Type(TypeName.Simple(name.text), name.span, Unit)
    }

    private fun parseId(): Token {
        return consume(IDENTIFIER) // can add soft keywords
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

    class ParserQueryable(private val queryEngine: QueryEngine) : Queryable<Key.UntypedAst, AstNode.File<Unit>> {

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