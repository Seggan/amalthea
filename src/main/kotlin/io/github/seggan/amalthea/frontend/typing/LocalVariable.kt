package io.github.seggan.amalthea.frontend.typing

data class LocalVariable(val name: String, val type: Type, val isMutable: Boolean, val scope: Int)