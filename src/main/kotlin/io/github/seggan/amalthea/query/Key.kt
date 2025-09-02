package io.github.seggan.amalthea.query

import io.github.seggan.amalthea.backend.compilation.AsmType
import io.github.seggan.amalthea.backend.compilation.CompiledFunction
import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.frontend.lexing.Token
import io.github.seggan.amalthea.frontend.parsing.AstNode
import io.github.seggan.amalthea.frontend.parsing.TypeName
import io.github.seggan.amalthea.frontend.typing.Signature
import io.github.seggan.amalthea.frontend.typing.Type
import io.github.seggan.amalthea.frontend.typing.TypeData

sealed interface Key<V : Any> {

    data class Tokens(val source: CodeSource) : Key<List<Token>>

    data class UntypedAst(val source: CodeSource) : Key<AstNode.File<Unit>>

    data class ResolvePackage(val pkg: List<String>) : Key<CodeSource>

    data class ResolveType(val type: TypeName) : Key<Type>

    data class ResolveHeader(val signature: Signature) : Key<AstNode.FunctionDeclaration<Unit>>

    data class ResolveFunctionCall(val name: QualifiedName, val args: List<Type>) : Key<Signature>

    data class TypeCheck(val signature: Signature) : Key<AstNode.FunctionDeclaration<TypeData>>

    data class Compile(val signature: Signature) : Key<CompiledFunction>

    data class RevolveSourceClass(val source: String) : Key<AsmType>
}