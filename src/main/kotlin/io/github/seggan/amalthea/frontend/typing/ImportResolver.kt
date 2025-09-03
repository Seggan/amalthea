package io.github.seggan.amalthea.frontend.typing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.Intrinsics
import io.github.seggan.amalthea.frontend.QualifiedName
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class ImportResolver(private val queryEngine: QueryEngine) : Queryable<Key.FindImport, QualifiedName> {
    override val keyType = Key.FindImport::class

    private val builtins =
        Type.builtinTypes.map { it.qName } union
            Intrinsics.entries.map { it.signature.name }

    override fun query(key: Key.FindImport): QualifiedName {
        val ast = queryEngine[Key.UntypedAst(key.context)]
        val imports = ast.imports.map { it.name }.toMutableSet()
        for (function in ast.declarations) {
            imports.add(QualifiedName(ast.pkg, function.name))
        }
        imports.addAll(builtins)
        val found = imports.filter { it.name == key.name }
        if (found.size > 1) {
            throw AmaltheaException(
                "Ambiguous reference to '${key.name}': could be ${found.joinToString(" or ") { "'$it'" }}",
            )
        }
        return found.singleOrNull() ?: throw AmaltheaException("Could not find import for '${key.name}'")
    }
}