package net.kleinhaneveld.tree

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

data class MyEdge(override val parent: String, override val child: String) : Edge<String>

private const val a = "A"
private const val b = "B"
private const val c = "C"
private const val d = "D"
private const val e = "E"
private val e1 = MyEdge(a, b)
private val e2 = MyEdge(a, c)
private val e3 = MyEdge(b, e)
private val e4 = MyEdge(c, d)
private val e5 = MyEdge(d, e)

fun getGraph(): Graph<String, MyEdge> {
    return Graph(setOf(a, b, c, d, e), setOf(e1, e2, e3, e4, e5), a)
}

fun getSubGraphOfB(): Graph<String, MyEdge> {
    return Graph(setOf(b, e), setOf(e3), b)
}

fun getSubGraphOfC(): Graph<String, MyEdge> {
    return Graph(setOf(c, d, e), setOf(e4, e5), c)
}

internal class GraphTest {
    @Test
    fun testThingy() {
        val myGraph = getGraph()

        println(myGraph.toString())
        println(myGraph.toDot())

        myGraph.inducedSubGraph("B").isEqualTo(getSubGraphOfB())

        myGraph.inducedSubGraph("C").isEqualTo(getSubGraphOfC())
    }
}

fun <V, E : Edge<V>>Graph<V, E>.isEqualTo(other: Graph<V, E>) {
    assertThat(root).isEqualTo(other.root)
    assertThat(vertices).containsAll(other.vertices)
    assertThat(edges).containsAll(other.edges)
}
