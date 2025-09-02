package io.github.seggan.amalthea.frontend

data class QualifiedName(val pkg: List<String>, val name: String) {

    val internalName = (pkg + name).joinToString("/")
    val descriptor = "L$internalName;"

    override fun toString() = (pkg + name).joinToString("::")

    companion object {
        fun amalthea(name: String) = QualifiedName(listOf("amalthea"), name)

        fun className(source: CodeSource): String =
            source.name.split('.').first().replaceFirstChar(Char::uppercase) + "Am"
    }
}