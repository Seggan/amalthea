package io.github.seggan.amalthea.backend

import io.github.seggan.amalthea.backend.compilation.AsmType
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class SourceClassComputer(@Suppress("unused") unused: QueryEngine) : Queryable<Key.ComputeSourceClass, AsmType> {
    override val keyType = Key.ComputeSourceClass::class

    override fun query(key: Key.ComputeSourceClass): AsmType {
        return AsmType.getObjectType(key.source.split('.').first() + "Am")
    }
}