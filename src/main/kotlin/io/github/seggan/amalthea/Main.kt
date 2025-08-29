package io.github.seggan.amalthea

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.lexing.Lexer
import io.github.seggan.amalthea.frontend.parsing.Parser.ParserQueryable
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.frontend.typing.FunctionResolver
import io.github.seggan.amalthea.frontend.typing.HeaderResolver
import io.github.seggan.amalthea.frontend.typing.TypeChecker
import io.github.seggan.amalthea.frontend.typing.TypeResolver
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import java.nio.file.FileSystems
import kotlin.io.path.Path
import kotlin.io.path.walk
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val matchers = args.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
    val currentDir = Path(System.getProperty("user.dir"))
    val codeSources = currentDir.walk()
        .filter { file ->
            val relative = currentDir.relativize(file)
            matchers.any { it.matches(relative) }
        }
        .map(CodeSource::file)
        .toList()
    val queryEngine = QueryEngine(codeSources)
    queryEngine.register(::Lexer)
    queryEngine.register(::ParserQueryable)
    queryEngine.register(::TypeResolver)
    queryEngine.register(::FunctionResolver)
    queryEngine.register(::HeaderResolver)
    queryEngine.register(::TypeChecker)

    try {
        val ast = queryEngine[Key.TypeCheck("main", emptyList(), TypeName.Simple("Unit"), "test.am")]
        println(ast)
    } catch (e: AmaltheaException) {
        System.err.println(e.report())
        exitProcess(1)
    }
}