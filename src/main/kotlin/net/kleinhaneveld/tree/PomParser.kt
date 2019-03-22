package net.kleinhaneveld.tree

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.FileFilter
import java.nio.file.Paths
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

data class MavenCoordinate(
        val groupId: String,
        val artifactId: String,
        val version: String?,
        val scope: String?,
        val type: String?,
        val sourceFiles: Int
)

data class MavenPom(
        val parent: MavenCoordinate?,
        val coordinate: MavenCoordinate,
        val dependencies: List<MavenCoordinate>
)

data class MavenVertex(
        val groupId: String,
        val artifactId: String,
        val type: String?,
        val sourceFiles: Int
) {
    fun toKey(): String = "$groupId:$artifactId"

    override fun toString(): String = "$groupId:$artifactId:$type - $sourceFiles"

    override fun equals(other: Any?): Boolean = when (other) {
        is MavenVertex -> Objects.equals(this.groupId, other.groupId) && Objects.equals(this.artifactId, other.artifactId)
        else -> false
    }

    override fun hashCode(): Int = Objects.hash(this.groupId, this.artifactId)
    // override fun hashCode(): Int = this.groupId.hashCode() + this.artifactId.hashCode()
}

fun MavenCoordinate.toVertex(): MavenVertex = MavenVertex(this.groupId, this.artifactId, this.type, this.sourceFiles)

data class MavenEdge(
        override val parent: MavenVertex,
        override val child: MavenVertex
) : Edge<MavenVertex>

val documentBuilderFactory = DocumentBuilderFactory.newInstance()!!

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
fun Node.single(tagName: String): Node = this.trySingle(tagName)
        ?: throw IllegalStateException("Expected single, but got no or multiple $tagName's")

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

fun Node.mavenCoordinate(sourceFiles: Int): MavenCoordinate {
    return MavenCoordinate(
            this.groupId(),
            this.artifactId(),
            this.tryVersion(),
            this.tryScope(),
            this.tryType(),
            sourceFiles
    )
}

fun Node.mavenCoordinate(parent: MavenCoordinate?, project: MavenCoordinate, sourceFiles: Int): MavenCoordinate {
    val groupId = this.groupId().replace("\${project.groupId}", project.groupId).replace("\${project.parent.groupId}", parent?.groupId
            ?: "")
    val artifactId = this.artifactId().replace("\${project.artifactId}", project.artifactId).replace("\${project.parent.artifactId}", parent?.artifactId
            ?: "")
    val version = this.tryVersion()?.replace("\${project.version}", project.version
            ?: "")?.replace("\${project.parent.version}", parent?.version ?: "")
    return MavenCoordinate(
            groupId,
            artifactId,
            version,
            this.tryScope(),
            this.tryType(),
            sourceFiles
    )
}

fun Node.mavenCoordinateUsingDefault(defaultMavenCoordinate: MavenCoordinate, sourceFiles: Int): MavenCoordinate {
    return MavenCoordinate(
            this.tryGroupId() ?: defaultMavenCoordinate.groupId,
            this.tryArtifactId() ?: defaultMavenCoordinate.artifactId,
            this.tryVersion() ?: defaultMavenCoordinate.version,
            this.tryScope(),
            this.tryType(),
            sourceFiles
    )
}

fun Node.tryChildrenOfSingle(tagName: String): Sequence<Node> = this.trySingle(tagName)?.childNodes?.asSequence()
        ?: emptySequence()

fun Node.dependencies(): Sequence<Element> = this.tryChildrenOfSingle("dependencies").filterIsInstance<Element>()

fun Document.mavenPom(sourceFiles: Int): MavenPom {
    val project = this.documentElement
    val parent: MavenCoordinate? = project.trySingle("parent")?.mavenCoordinate(sourceFiles)
    val pomCoordinate: MavenCoordinate = if (parent != null) {
        project.mavenCoordinateUsingDefault(parent, sourceFiles)
    } else {
        project.mavenCoordinate(sourceFiles)
    }
    val correctedPomCoordinate = pomCoordinate.copy(type = pomCoordinate.type ?: "jar")
    val dependencies: List<MavenCoordinate> = project
            .dependencies()
            .map { it.mavenCoordinate(parent, pomCoordinate, sourceFiles) }
            .toList()
    return MavenPom(parent, correctedPomCoordinate, dependencies)
}

fun parsePom(fileName: File): MavenPom {
    val pomDocument = documentBuilderFactory.newDocumentBuilder().parse(fileName)
    val sourceFiles = File(File(fileName.parentFile, "src"), "main").walkBottomUp()
            .filter { it.isFile }
            .count()
    return pomDocument.mavenPom(sourceFiles)
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
    return if (this.parent != null) {
        dependencies // + MavenEdge(parent, this.parent.toVertex())
    } else {
        dependencies
    }
}

fun mavenGraph(mavenProjectDirectory: File): Graph<MavenVertex, MavenEdge> {
    val poms = parseDir(mavenProjectDirectory)

    val vertices: Set<MavenVertex> = poms.map { it.coordinate.toVertex() }.toSet()
    val vertexMap: Map<String, MavenVertex> = vertices.associateBy { it.toKey() }
    val edges: Set<MavenEdge> = poms.flatMap { it.edges() }
            .filter { it.parent in vertices && it.child in vertices }
            .map { MavenEdge(it.parent, vertexMap.getOrDefault(it.child.toKey(), it.child)) }
            .toSet()
    val root = poms.last().coordinate.toVertex()
    return Graph(vertices, edges, root)
}

class TopLevelModules : CliktCommand(name = "top-level-modules") {
    private val directory: File by option(help = "Maven project directory (default is current working directory)").file().default(
            Paths.get("").toAbsolutePath().toFile()
    )
    private val type: String? by option()
    private val skipTests: Boolean by option("--skip-tests").flag("--with-tests", default = false)
    override fun run() {
        val graph = mavenGraph(directory)
        graph.vertices.filter { graph.orderIncoming(it) == 0 }
                .filter {
                    (type == null || it.type?.equals(type) ?: true)
                            && (!skipTests || !it.artifactId.contains("-test"))
                }
                .forEach { println(it) }
    }
}

class ModuleSourceFiles : CliktCommand(name = "module-source-files") {
    val directory: File by option(help = "Maven project directory (default is current working directory)")
            .file()
            .default(Paths.get("").toAbsolutePath().toFile())

    override fun run() {
        val graph = mavenGraph(directory)
        graph.vertices.filter { it.type?.equals("jar") ?: true }
                .sortedWith(kotlin.Comparator { o1, o2 -> o2.sourceFiles - o1.sourceFiles })
                .forEach { println(it) }
    }
}

class SubGraphDot : CliktCommand(name = "subgraph-dot") {
    val directory: File by option(help = "Maven project directory (default is current working directory)")
            .file()
            .default(Paths.get("").toAbsolutePath().toFile())
    val module by argument("Module to investigate")

    override fun run() {
        val graph = mavenGraph(directory)
        val vertex = toMavenVertex(module, graph)
        if (vertex != null){
            println(graph.inducedSubGraph(vertex).toDot())
        } else {
            System.err.println("Module '$module' not found")
        }
    }
}

fun <T>T?.ifNull(code: () -> Unit): T? {
    if (this == null) {
        code.invoke()
    }
    return this
}

class RemoveModule : CliktCommand(name = "remove-modules") {
    val directory: File by option(help = "Maven project directory (default is current working directory)")
            .file()
            .default(Paths.get("").toAbsolutePath().toFile())
    val modules by argument("Modules to delete").multiple(true)
    override fun run() {
        val graph = mavenGraph(directory)
        val subGraphs = modules.asSequence()
                .map { module ->
                    toMavenVertex(module, graph).ifNull {
                        System.err.println("Module not found: $module")
                    }
                }
                .filterNotNull()
                .filter {
                    if (graph.orderIncoming(it) == 0) {
                        true
                    } else {
                        System.err.println("$it cannot be deleted")
                        false
                    }
                }
                .map {
                    System.err.println("Calculating inducedSubGraph of $it")
                    graph.inducedSubGraph(it)
                }
                .toList()

        System.err.println("Calculating subGraph vertices")
        val subGraphVertices = subGraphs.asSequence()
                .flatMap { it.vertices.asSequence() }
                .toSet()

        val vDiff = graph.vertices - subGraphVertices
        val incomingEdges = graph.edges.filter {
            vDiff.contains(it.parent) && subGraphVertices.contains(it.child)
        }.toSet()

        val result: MutableSet<MavenVertex> = subGraphVertices.toMutableSet()

        incomingEdges
                .map { it.child }
                .toSet()
                .parallelStream()
                .forEach {
                    System.err.println("Calculating inducedSubGraph of ${it}")
                    result.removeAll(graph.inducedSubGraph(it).vertices)
                }

        result.forEach { println(it) }
    }
}

private fun toMavenVertex(module: String, graph: Graph<MavenVertex, MavenEdge>): MavenVertex? {
    val moduleComponents = module.split(':')
    return graph.vertices.find {
        it.groupId == moduleComponents[0] && it.artifactId == moduleComponents[1]
    }
}

class MavenDependencies : CliktCommand(name = "maven-dependencies") {
    override fun run() {

    }
}

fun main(args: Array<String>) {
    MavenDependencies().subcommands(
            TopLevelModules(),
            ModuleSourceFiles(),
            RemoveModule(),
            SubGraphDot()).main(args)
}
