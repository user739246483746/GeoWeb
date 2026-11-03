package dev.motor.engine

sealed class Node
class Text(val text: String) : Node()
class Element(
    val tag: String,
    val attrs: Map<String, String>,
    var parent: Element? = null
) : Node() {
    val depth: Int = (parent?.depth ?: -1) + 1
    val children = mutableListOf<Node>()
    val id: String? get() = attrs["id"]
    val classList: List<String> by lazy {
        (attrs["class"] ?: "").split(' ', '\t', '\n', '\r').filter { it.isNotEmpty() }
    }
}
