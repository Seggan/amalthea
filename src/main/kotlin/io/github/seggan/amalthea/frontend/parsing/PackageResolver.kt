package io.github.seggan.amalthea.frontend.parsing

import io.github.seggan.amalthea.frontend.AmaltheaException
import io.github.seggan.amalthea.frontend.CodeSource
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class PackageResolver(private val queryEngine: QueryEngine) : Queryable<Key.ResolvePackage, CodeSource> {
    override val keyType = Key.ResolvePackage::class

    override fun query(key: Key.ResolvePackage): CodeSource {
        for (source in queryEngine.sources) {
            val untypedAst = queryEngine[Key.UntypedAst(source)]
            if (untypedAst.pkg == key.pkg) {
                return source
            }
        }
        throw AmaltheaException("Cannot find package '${key.pkg.joinToString("::")}'")
    }
}