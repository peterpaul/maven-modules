package net.kleinhaneveld.tree

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class MavenCoordinate (
        val groupId: String,
        val artifactId: String,
        val version: String
        )

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

fun Element.single(tagName: String): Node = this.getElementsByTagName(tagName).asSequence().single()

fun Element.trySingle(tagName: String): Node? = this.getElementsByTagName(tagName).asSequence().singleOrNull()

fun Node.single(tagName: String): Node = (this as Element).single(tagName)

fun Node.trySingle(tagName: String): Node? = when (this) {
    is Element -> this.single(tagName)
    else -> null
}

fun Node.groupId(): String = this.single("groupId").textContent
fun Node.artifactId(): String = this.single("artifactId").textContent
fun Node.version(): String = this.single("version").textContent
fun Node?.tryGroupId(): String? = this?.trySingle("groupId")?.textContent
fun Node?.tryArtifactId(): String? = this?.trySingle("artifactId")?.textContent
fun Node?.tryVersion(): String? = this?.trySingle("version")?.textContent

fun Node.mavenCoordinate(): MavenCoordinate {
    return MavenCoordinate(
            this.groupId(),
            this.artifactId(),
            this.version()
    )
}

fun Node.mavenCoordinateUsingDefault(defaultMavenCoordinate: MavenCoordinate): MavenCoordinate {
    return MavenCoordinate(
            this.tryGroupId() ?: defaultMavenCoordinate.groupId,
            this.tryArtifactId() ?: defaultMavenCoordinate.artifactId,
            this.tryVersion() ?: defaultMavenCoordinate.version
    )
}

fun parsePom(fileName: File) {
    val pomDocument = documentBuilderFactory.newDocumentBuilder().parse(fileName)
    val project = pomDocument.documentElement
    val parent = project.trySingle("parent")
    val parentCoordinate = parent!!.mavenCoordinate()
    println(parentCoordinate)
    println(project.mavenCoordinateUsingDefault(parentCoordinate))
}
