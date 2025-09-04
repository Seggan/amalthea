package io.github.seggan.amalthea.frontend.typing.function

import io.github.seggan.amalthea.frontend.typing.Type

data class LocalVariable(val id: Int, val name: String, val type: Type, val isMutable: Boolean)