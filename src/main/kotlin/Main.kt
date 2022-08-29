
import java.io.File

fun main(args: Array<String>) {
    val path = if (args.isNotEmpty()) args[0] else null

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${args.joinToString()}")
}
