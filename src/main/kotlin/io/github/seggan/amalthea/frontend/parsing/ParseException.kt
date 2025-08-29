package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Span
import io.github.seggan.amalthea.frontend.lexing.Token

open class ParseException(message: String, span: Span) : AmaltheaException(message, mutableListOf(span))

class UnexpectedTokenException(val expected: List<Token.Type>, actual: String, span: Span) :
    ParseException("Unexpected $actual, expected ${expected.joinToString(" or ")}", span)