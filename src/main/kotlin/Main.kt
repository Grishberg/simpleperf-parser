import com.github.grishberg.simpleperf.Parser
import java.io.File

fun main(args: Array<String>) {
    val path = args[0]

    val parser = Parser(File(path))
    parser.parse()
    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")
}