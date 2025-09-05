package io.github.seggan.amalthea

import io.github.seggan.amalthea.backend.OutputClass
import io.github.seggan.amalthea.backend.asmTypeName
import io.github.seggan.amalthea.backend.function.FunctionCompiler
import io.github.seggan.amalthea.backend.struct.CompiledStruct
import io.github.seggan.amalthea.backend.struct.StructCompiler
import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.lexing.Lexer
import io.github.seggan.amalthea.frontend.parsing.PackageResolver
import io.github.seggan.amalthea.frontend.parsing.Parser
import io.github.seggan.amalthea.frontend.typing.ImportResolver
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.TypeResolver
import io.github.seggan.amalthea.frontend.typing.function.*
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.FileSystems
import kotlin.io.path.*
import kotlin.system.exitProcess

@OptIn(ExperimentalPathApi::class)
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
    queryEngine.register(Parser::QueryProvider)
    queryEngine.register(::PackageResolver)
    queryEngine.register(::TypeResolver)
    queryEngine.register(::ImportResolver)
    queryEngine.register(::FunctionCallResolver)
    queryEngine.register(::FunctionFinder)
    queryEngine.register(::HeaderResolver)
    queryEngine.register(FunctionTypeChecker::QueryProvider)
    queryEngine.register(FunctionCompiler::QueryProvider)
    queryEngine.register(::StructCompiler)

    try {
        val compiled = queryEngine[Key.CompileFunction(
            Signature(
                QualifiedName(listOf("test"), "main"),
                Type.Function(emptyList(), Type.Unit)
            )
        )]

        val outPath = currentDir.resolve("out")
        outPath.deleteRecursively()

        val structsUsed = mutableSetOf<CompiledStruct>()
        val writers = mutableMapOf<OutputClass, ClassWriter>()
        for (function in compiled.functionsUsed) {
            val cw = writers.getOrPut(function.outputClass) {
                val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                cw.visit(
                    Opcodes.V21,
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL,
                    function.outputClass.jvmName,
                    null,
                    asmTypeName<Any>(),
                    null
                )
                cw
            }
            function.createIn(cw)
            structsUsed.addAll(function.structsUsed)
        }
        for ((outputClass, classWriter) in writers) {
            classWriter.visitEnd()
            outputClass.write(outPath, classWriter.toByteArray())
        }
        for (struct in structsUsed) {
            struct.outputClass.write(outPath, struct.bytecode)
        }

        Runtime.getRuntime().exec(
            arrayOf(
                "jar",
                "cfe",
                "out.jar",
                "${compiled.outputClass.pkg.joinToString(".")}.${compiled.outputClass.name}",
                "-C",
                outPath.absolutePathString(),
                "."
            )
        ).waitFor()
        outPath.deleteRecursively()
    } catch (e: AmaltheaException) {
        System.err.println(e.report())
        exitProcess(1)
    }
}