package io.github.seggan.amalthea.backend

import io.github.seggan.amalthea.backend.compilation.AsmType
import io.github.seggan.amalthea.query.Key
import io.github.seggan.amalthea.query.QueryEngine
import io.github.seggan.amalthea.query.Queryable

class SourceClassResolver(@Suppress("unused") unused: QueryEngine) : Queryable<Key.RevolveSourceClass, AsmType> {
    override val keyType = Key.RevolveSourceClass::class

    override fun query(key: Key.RevolveSourceClass): AsmType {
        return AsmType.getObjectType(key.source.split('.').first() + "Am")
    }
}