package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Span

class ParseException(message: String, span: Span) : AmaltheaException(message, mutableListOf(span))