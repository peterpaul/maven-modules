package net.kleinhaneveld.tree

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.FileFilter
import javax.xml.parsers.DocumentBuilderFactory

data class MavenCoordinate (
        val groupId: String,
        val artifactId: String,
        val version: String?,
        val scope: String?,
        val type: String?
)

data class MavenPom (
        val parent: MavenCoordinate?,
        val coordinate: MavenCoordinate,
        val dependencies: List<MavenCoordinate>
)

data class MavenVertex (
        val groupId: String,
        val artifactId: String
) {
    override fun toString(): String {
        return "$groupId:$artifactId"
    }
}

fun MavenCoordinate.toVertex(): MavenVertex = MavenVertex(this.groupId, this.artifactId)

data class MavenEdge (
        override val parent: MavenVertex,
        override val child: MavenVertex
) : Edge<MavenVertex>

val documentBuilderFactory = DocumentBuilderFactory.newInstance()

class NodeListIterator(private val nodeList: NodeList) : Iterator<Node> {
    private var currentPosition = 0

    override fun hasNext(): Boolean = currentPosition < nodeList.length

    override fun next(): Node {
        if (hasNext()) {
            val out = nodeList.item(currentPosition)
            currentPosition++
            return out
        } else {
            throw NoSuchElementException()
        }
    }
}

fun NodeList.iterator(): Iterator<Node> = NodeListIterator(this)
fun NodeList.asSequence(): Sequence<Node> = Sequence { this.iterator() }
fun Node.single(tagName: String): Node = this.trySingle(tagName) ?: throw IllegalStateException("Expected single, but got no or multiple $tagName's")

fun Node.trySingle(tagName: String): Node? = when (this) {
    is Element -> this.childNodes
            .asSequence()
            .filterIsInstance<Element>()
            .filter { it.nodeName == tagName }
            .singleOrNull()
    else -> null
}

fun Node.groupId(): String = this.single("groupId").textContent
fun Node.artifactId(): String = this.single("artifactId").textContent
fun Node?.tryGroupId(): String? = this?.trySingle("groupId")?.textContent
fun Node?.tryArtifactId(): String? = this?.trySingle("artifactId")?.textContent
fun Node?.tryVersion(): String? = this?.trySingle("version")?.textContent
fun Node?.tryScope(): String? = this?.trySingle("scope")?.textContent
fun Node?.tryType(): String? = this?.trySingle("type")?.textContent ?: this?.trySingle("packaging")?.textContent

fun Node.mavenCoordinate(): MavenCoordinate {
    return MavenCoordinate(
            this.groupId(),
            this.artifactId(),
            this.tryVersion(),
            this.tryScope(),
            this.tryType()
    )
}

fun Node.mavenCoordinateUsingDefault(defaultMavenCoordinate: MavenCoordinate): MavenCoordinate {
    return MavenCoordinate(
            this.tryGroupId() ?: defaultMavenCoordinate.groupId,
            this.tryArtifactId() ?: defaultMavenCoordinate.artifactId,
            this.tryVersion() ?: defaultMavenCoordinate.version,
            this.tryScope(),
            this.tryType()
    )
}

fun Node.tryChildrenOfSingle(tagName: String): Sequence<Node> = this.trySingle(tagName)?.childNodes?.asSequence() ?: emptySequence()
fun Node.dependencies(): Sequence<Element> = this.tryChildrenOfSingle("dependencies").filterIsInstance<Element>()

fun Document.mavenPom(): MavenPom {
    val project = this.documentElement
    val parent: MavenCoordinate? = project.trySingle("parent")?.mavenCoordinate()
    val pomCoordinate: MavenCoordinate = if (parent != null) {
        project.mavenCoordinateUsingDefault(parent)
    } else {
        project.mavenCoordinate()
    }
    val dependencies: List<MavenCoordinate> = project
            .dependencies()
            .map { it.mavenCoordinate() }
            .toList()
    return MavenPom(parent, pomCoordinate, dependencies)
}

fun parsePom(fileName: File): MavenPom {
    val pomDocument = documentBuilderFactory.newDocumentBuilder().parse(fileName)
    val out = pomDocument.mavenPom()
    println(out)
    return out
}

fun parseDir(fileName: File): List<MavenPom> {
    return if (fileName.isDirectory) {
        fileName.listFiles(FileFilter { it.isDirectory && File(it, "pom.xml").exists() })
                .flatMap { parseDir(it) }
                .toList() + parsePom(File(fileName, "pom.xml"))
    } else {
        emptyList()
    }
}

fun MavenPom.edges(): List<MavenEdge> {
    val parent = this.coordinate.toVertex()
    val dependencies = this.dependencies.map { MavenEdge(parent, it.toVertex()) }
    if (this.parent != null) {
        return dependencies + MavenEdge(parent, this.parent.toVertex())
    } else {
        return dependencies
    }
}

fun main(args: Array<String>) {
    val poms = parseDir(File("/home/peterpaulk/projects/federation"))
    println("size: ${poms.size}")

    val vertices: Set<MavenVertex> = poms.map { it.coordinate.toVertex() }.toSet()
    println("vertices size = ${vertices.size}")
    val edges: Set<MavenEdge> = poms.flatMap { it.edges() }.filter { it.parent in vertices && it.child in vertices }.toSet()
    println("edges size = ${edges.size}")
    val root = poms.last().coordinate.toVertex()
    println("root: $root")
    val graph = Graph(vertices, edges, root)

    vertices.filter { graph.orderIncoming(it) == 0 }.forEach { println(it) }
}