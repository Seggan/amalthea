package io.github.seggan.amalthea.app

import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.query.QueryEngine
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

fun main(args: Array<String>) {
    val currentDir = Path(System.getProperty("user.dir"))
    val codeSources = args.flatMap(currentDir::listDirectoryEntries).map(CodeSource::file)
    val queryEngine = QueryEngine(codeSources)
}