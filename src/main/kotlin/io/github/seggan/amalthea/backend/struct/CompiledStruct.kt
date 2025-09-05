package io.github.seggan.amalthea.backend.struct

import io.github.seggan.amalthea.backend.OutputClass
import io.github.seggan.amalthea.frontend.typing.Type

class CompiledStruct(val struct: Type.Struct, val bytecode: ByteArray, dependencies: Set<CompiledStruct>) {

    val dependencies: Set<CompiledStruct> by lazy {
        dependencies.flatMapTo(mutableSetOf()) { it.dependencies } + dependencies + this
    }

    val outputClass = OutputClass(struct.qName.pkg, struct.qName.name)

    override fun equals(other: Any?): Boolean = other is CompiledStruct && struct == other.struct
    override fun hashCode(): Int = struct.hashCode()
}