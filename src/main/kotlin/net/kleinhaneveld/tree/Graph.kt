package net.kleinhaneveld.tree

import java.util.stream.Collectors

interface Edge<out V> {
    val parent: V
    val child: V
}

private fun <V, E : Edge<V>>edgesOfVertices(edges: Set<E>): Map<V, Set<E>> {
    fun extractEdgesOfVertexMapFromEdge(e: E): Map<V, Set<E>> =
            mapOf(e.parent to setOf(e), e.child to setOf(e))

    fun concatenateEdgesOfVertexMaps(a: Map<V, Set<E>>, b: Map<V, Set<E>>): Map<V, Set<E>> {
        fun concatenateSetOfEdgesForVertex(v: V, a: Map<V, Set<E>>, b: Map<V, Set<E>>): Map<V, Set<E>> {
            val concatenatedSetOfEdges: Set<E> = a.getOrDefault(v, emptySet()) + b.getOrDefault(v, emptySet())
            return mapOf(v to concatenatedSetOfEdges)
        }

        val vertices: Set<V> = a.keys + b.keys
        return vertices
                .parallelStream()
                .map { v -> concatenateSetOfEdgesForVertex(v, a, b) }
                .reduce { x, y -> x + y }
                .get()
    }

    val edgesOfVertexMaps: Set<Map<V, Set<E>>> = edges
            .parallelStream()
            .map { e -> extractEdgesOfVertexMapFromEdge(e) }
            .collect(Collectors.toSet())

    return edgesOfVertexMaps.fold(emptyMap()) { a, b -> concatenateEdgesOfVertexMaps(a, b)}
}

class Graph<V, out E : Edge<V>> (
        val vertices: Set<V>,
        val edges: Set<E>,
        val root: V) {
    private val edgesOfVertexMap: Map<V, Set<E>> by lazy { edgesOfVertices(edges) }

    fun edgesOfVertex(v: V): Set<E> = edgesOfVertexMap[v] ?: emptySet()

    fun removeAll(vToRemove: Set<V>): Graph<V, E> {
        val eToRemove: Set<E> = vToRemove.map { edgesOfVertex(it) }.reduce { a, b -> a + b }
        val newVs: Set<V> = vertices.filter { !vToRemove.contains(it) }.toSet()
        val eNew = edges.filter { !eToRemove.contains(it) }.toSet()
        return Graph(newVs, eNew, root)
    }

    fun childrenOfVertex(v: V): Set<V> = outgoingEdgesOfVertex(v).map { e -> e.child }.toSet()

    fun verifyVertexExists(v: V) {
        if (!vertices.contains(v)) {
            throw IllegalStateException("Vertex '$v' does not exist in Graph '$this'")
        }
    }

    fun inducedSubGraph(v: V): Graph<V, E> {
        fun subGraph(v: V): Pair<Set<V>, Set<E>> {
            fun union(a: Pair<Set<V>, Set<E>>, b: Pair<Set<V>, Set<E>>): Pair<Set<V>, Set<E>> = Pair(a.first + b.first, a.second + b.second)
            return if (orderOutgoing(v) > 0) {
                val combinedSubGraphs: Pair<Set<V>, Set<E>> = childrenOfVertex(v)
                        .parallelStream()
                        .map { subGraph(it) }
                        .reduce { a, b -> union(a, b) }
                        .get()
                union(combinedSubGraphs, Pair(setOf(v), outgoingEdgesOfVertex(v)))
            } else {
                Pair(setOf(v), emptySet())
            }
        }

        verifyVertexExists(v)
        val subgraph: Pair<Set<V>, Set<E>> = subGraph(v)
        return Graph(subgraph.first, subgraph.second, v)
    }

    fun incomingEdgesOfVertex(v: V): Set<E> = edgesOfVertex(v).filter { it.child == v }.toSet()
    fun outgoingEdgesOfVertex(v: V): Set<E> = edgesOfVertex(v).filter { it.parent == v }.toSet()

    fun orderOutgoing(v: V): Int = edgesOfVertex(v).count { it.parent == v }

    fun orderIncoming(v: V): Int = edgesOfVertex(v).count { it.child == v }

    fun order(v: V): Int = edgesOfVertex(v).size

    fun parentsOfVertex(v: V): Set<V> = incomingEdgesOfVertex(v).map { it.parent }.toSet()

    override fun toString(): String {
        return "G(V($vertices), E($edges), $root)"
    }

    fun toDot(): String {
        val builder = StringBuilder()
        builder.append("digraph G {\n")
        for (v in vertices) {
            builder.append("\"$v\";\n")
        }
        for (e in edges) {
            builder.append("\"" + e.parent + "\" -> \"" + e.child + "\" [label=\"" + e + "\"];\n")
        }
        builder.append("}")
        return builder.toString()
    }
}

data class MyEdge(override val parent: String, override val child: String) : Edge<String>

fun main(args: Array<String>) {
    val a = "A"
    val b = "B"
    val c = "C"
    val d = "D"
    val e = "E"
    val e1 = MyEdge(a, b)
    val e2 = MyEdge(a, c)
    val e3 = MyEdge(b, e)
    val e4 = MyEdge(c, d)
    val e5 = MyEdge(d, e)
    val myGraph = Graph(setOf(a, b, c, d, e), setOf(e1, e2, e3, e4, e5), a)
    println(myGraph.toString())
    println(myGraph.toDot())

    println(myGraph.inducedSubGraph(b).toDot())

    println(myGraph.inducedSubGraph(c).toDot())
}
