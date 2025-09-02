package io.github.seggan.amalthea

import io.github.seggan.amalthea.backend.SourceClassResolver
import io.github.seggan.amalthea.backend.compilation.CompiledFunction
import io.github.seggan.amalthea.backend.compilation.FunctionCompiler
import io.github.seggan.amalthea.backend.compilation.asmTypeName
import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.lexing.Lexer
import io.github.seggan.amalthea.frontend.parsing.PackageResolver
import io.github.seggan.amalthea.frontend.parsing.Parser
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.frontend.typing.*
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.nio.file.FileSystems
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.io.path.Path
import kotlin.io.path.walk
import kotlin.io.path.writeBytes
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
    queryEngine.register(Parser::QueryProvider)
    queryEngine.register(::PackageResolver)
    queryEngine.register(::TypeResolver)
    queryEngine.register(::FunctionResolver)
    queryEngine.register(::HeaderResolver)
    queryEngine.register(::TypeChecker)
    queryEngine.register(::SourceClassResolver)
    queryEngine.register(FunctionCompiler::QueryProvider)

    try {
        val compiled = queryEngine[Key.Compile(
            QualifiedName(listOf("test"), "main"),
            TypeName.Function(
                listOf(),
                TypeName.Simple(Type.Unit.qName)
            )
        )]
        val visited: MutableSet<Signature> = Collections.newSetFromMap(IdentityHashMap())
        fun gatherFunctions(fn: CompiledFunction): Set<CompiledFunction> {
            if (fn.signature in visited) return emptySet()
            return fn.dependencies + fn
        }

        val functions = gatherFunctions(compiled).associateWith {
            queryEngine[Key.ResolvePackage(it.signature.name.pkg)]
        }
        for (source in codeSources) {
            val sourceFns = functions.filterValues { it == source }.keys
            if (sourceFns.isEmpty()) continue
            var name = source.name.split('.').first() + "Am"
            name = name[0].uppercase() + name.drop(1)
            val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
            cw.visit(
                Opcodes.V21,
                Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL,
                name,
                null,
                asmTypeName<Any>(),
                null
            )
            for (function in sourceFns) {
                function.createIn(cw)
            }
            cw.visitEnd()
            Path("$name.class").writeBytes(cw.toByteArray())
        }
    } catch (e: AmaltheaException) {
        System.err.println(e.report())
        exitProcess(1)
    }
}