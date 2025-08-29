package io.github.seggan.amalthea

import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.lexing.Lexer
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import java.nio.file.FileSystems
import kotlin.io.path.Path
import kotlin.io.path.walk

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

    for (source in codeSources) {
        println("Lexing ${source.name}:")
        val tokens = queryEngine[Key.Tokens(source.name)]
        println(tokens)
    }
}