package net.kleinhaneveld.tree

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Paths
import java.util.stream.Collectors

abstract class BaseCommand(help: String, name: String) : CliktCommand(help = help, name = name) {
    private val files by requireObject<Map<String, File>>()
    private val flags by requireObject<Map<String, Boolean>>()
    private fun directory(): File = files["DIRECTORY"] ?: throw IllegalStateException()
    private fun verbose(): Boolean = flags["VERBOSE"] ?: throw IllegalStateException()
    fun mavenGraph(): Graph<MavenVertex, MavenEdge> = mavenGraph(directory())
    fun debug(line: String) {
        if (verbose()) {
            System.err.println(line)
        }
    }

    fun error(line: String) = System.err.println(line)
}

class TopLevelModules : BaseCommand(
        name = "top-level-modules",
        help = "List all top-level modules in the repository. " +
                "Top-level modules are modules that are not depended on by other modules in this repository.") {
    private val type: String? by option(help = "Module types to list (e.g. jar, war, pom).")
    private val skipType: String? by option("--skip-type", help = "Module types to skip (e.g. jar, war, pom)")
    private val skipTests: Boolean by option("--skip-tests",
            help = "Flag to control filtering of test modules. Test modules are modules with '-test' in the artifactId.")
            .flag("--with-tests", default = false)

    override fun run() {
        val graph = mavenGraph()
        graph.vertices.filter { graph.orderIncoming(it) == 0 }
                .filter {
                    (type == null || it.type?.equals(type) ?: true)
                            && (skipType == null || !(it.type?.equals(skipType) ?: false))
                            && (!skipTests || !it.artifactId.contains("-test"))
                }
                .forEach { println(it) }
    }
}

class ModuleSourceFiles : BaseCommand(
        name = "module-source-files",
        help = "List all modules sorted by number of source files."
) {
    private val type: String? by option(help = "Module types to list (e.g. jar, war, pom).")
    private val skipType: String? by option("--skip-type", help = "Module types to skip (e.g. jar, war, pom)")
    private val skipTests: Boolean by option("--skip-tests",
            help = "Flag to control filtering of test modules. Test modules are modules with '-test' in the artifactId.")
            .flag("--with-tests", default = false)

    override fun run() {
        val graph = mavenGraph()
        graph.vertices.asSequence()
                .filter {
                    (type == null || it.type?.equals(type) ?: true)
                            && (skipType == null || !(it.type?.equals(skipType) ?: false))
                            && (!skipTests || !it.artifactId.contains("-test"))
                }
                .sortedWith(kotlin.Comparator { o1, o2 -> o2.sourceFiles - o1.sourceFiles })
                .forEach { println(it) }
    }
}

class ModuleDependencyCount : BaseCommand(name = "module-dependency-count",
        help = "List all modules in the repository sorted by dependency count.") {
    private val type: String? by option(help = "Module types to list (e.g. jar, war, pom).")
    private val skipType: String? by option("--skip-type", help = "Module types to skip (e.g. jar, war, pom)")
    private val skipTests: Boolean by option("--skip-tests",
            help = "Flag to control filtering of test modules. Test modules are modules with '-test' in the artifactId.")
            .flag("--with-tests", default = false)

    override fun run() {
        val graph = mavenGraph()
        graph.vertices.asSequence()
                .filter {
                    (type == null || it.type?.equals(type) ?: true)
                            && (skipType == null || !(it.type?.equals(skipType) ?: false))
                            && (!skipTests || !it.artifactId.contains("-test"))
                }
                .map {
                    val orderIncoming = graph.orderIncoming(it)
                    Pair(orderIncoming, it)
                }
                .sortedWith(kotlin.Comparator { a, b -> b.first - a.first })
                .forEach {
                    println("${it.first} - ${it.second}")
                }
    }
}

class SubGraphDot : BaseCommand(name = "subgraph-dot",
        help = "Generate dot graph for MODULE as OUTPUT-FILE.") {
    private val module by argument(help = "Module to investigate")
    private val outputType: String by option("--image-type", "-t", help = "Image type (default: 'png')").default("png")
    private val dotFile: String by option("--dot-file", "-f", help = "File name for dot file (default: 'subgraph.dot')").default("subgraph.dot")
    private val outputFile: String by argument("OUTPUT-FILE", help = "Target file for image")

    override fun run() {
        val graph = mavenGraph()
        val vertex = toMavenVertex(module, graph)
        val dot: String = if (vertex != null) {
            graph.inducedSubGraph(vertex).toDot()
        } else {
            error("Module '$module' not found")
            System.exit(1)
            ""
        }
        PrintStream(FileOutputStream(dotFile)).print(dot)
        val command = arrayOf("dot", "-T$outputType", dotFile, "-o", outputFile)
        val process: Process = Runtime.getRuntime().exec(command)
        val err: Int = process.waitFor()
        if (err != 0) {
            error("Failed to generate dot:\n$dot")
        }
    }
}

fun <T> T?.ifNull(code: () -> Unit): T? {
    if (this == null) {
        code.invoke()
    }
    return this
}

class RemoveModule : BaseCommand(
        name = "delete-modules",
        help = "Analyzes what modules can be removed from the repository, when deleting MODULES.") {
    private val modules by argument(help = "Top-level modules to delete").multiple(true)

    override fun run() {
        val graph = mavenGraph()
        val subGraphs = modules.asSequence()
                .map { module ->
                    toMavenVertex(module, graph).ifNull {
                        error("Module not found: $module")
                    }
                }
                .filterNotNull()
                .filter {
                    if (graph.orderIncoming(it) == 0) {
                        true
                    } else {
                        error("$it cannot be deleted")
                        false
                    }
                }
                .map {
                    debug("Calculating inducedSubGraph of $it")
                    graph.inducedSubGraph(it)
                }
                .toList()

        debug("Calculating subGraph vertices")
        val subGraphVertices = subGraphs.asSequence()
                .flatMap { it.vertices.asSequence() }
                .toSet()

        val vDiff = graph.vertices - subGraphVertices
        val incomingEdges = graph.edges.filter {
            vDiff.contains(it.parent) && subGraphVertices.contains(it.child)
        }.toSet()

        val result: MutableSet<MavenVertex> = subGraphVertices.toMutableSet()

        incomingEdges
                .parallelStream()
                .map { it.child }
                .collect(Collectors.toSet())
                .parallelStream()
                .flatMap {
                    debug("Calculating inducedSubGraph of $it")
                    graph.inducedSubGraph(it).vertices.stream()
                }
                .forEach {
                    result.remove(it)
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

class MavenDependencies : CliktCommand(name = "maven-modules") {
    val files by findObject { mutableMapOf<String, File>() }
    val flags by findObject { mutableMapOf<String, Boolean>() }
    val directory: File by option(help = "Maven project directory (default is current working directory)")
            .file()
            .default(Paths.get("").toAbsolutePath().toFile())
    val verbose: Boolean by option("--verbose", "-v", help = "Write debug output to stderr.").flag("--quiet", "-q", default = false)
    override fun run() {
        files["DIRECTORY"] = directory
        flags["VERBOSE"] = verbose
    }
}

fun main(args: Array<String>) {
    MavenDependencies().subcommands(
            TopLevelModules(),
            ModuleSourceFiles(),
            ModuleDependencyCount(),
            RemoveModule(),
            SubGraphDot()).main(args)
}
