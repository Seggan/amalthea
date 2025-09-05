package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.backend.function.CompiledFunction
import io.github.seggan.amalthea.backend.struct.CompiledStruct
import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.TypeData
import io.github.seggan.amalthea.frontend.typing.function.Signature

sealed interface Key<V> {

    data class Tokens(val source: CodeSource) : Key<List<Token>>

    data class UntypedAst(val source: CodeSource) : Key<AstNode.File<Unit>>

    data class ResolvePackage(val pkg: List<String>) : Key<CodeSource>

    data class FindImport(val name: String, val context: CodeSource) : Key<QualifiedName>

    data class ResolveType(val type: TypeName, val context: CodeSource) : Key<Type>

    data class ResolveHeader(val pkg: List<String>, val function: AstNode.Function<Unit>) : Key<Signature> {
        override fun toString(): String {
            return "ResolveHeader(pkg=$pkg, function=${function.name}(${
                function.parameters.joinToString { (name, type) -> "$name: $type" }
            }): ${function.returnType})"
        }
    }

    data class FindFunctionBody(val signature: Signature) : Key<AstNode.Function<Unit>>

    data class ResolveFunctionCall(val name: QualifiedName, val args: List<Type>, val context: CodeSource) : Key<Signature>

    data class TypeCheckFunction(val signature: Signature) : Key<AstNode.Function<TypeData>>

    data class CompileFunction(val signature: Signature) : Key<CompiledFunction>

    data class CompileStruct(val struct: Type.Struct) : Key<CompiledStruct>
}