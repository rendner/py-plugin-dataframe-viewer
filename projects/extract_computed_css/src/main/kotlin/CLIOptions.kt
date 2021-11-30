import org.apache.commons.cli.*
import kotlin.system.exitProcess


fun createCLOptions(): Options {
    return Options().apply {
        this.addOption("h", "help", false, "print this message")
        this.addRequiredOption("d", "directory", true, "input directory, searched recursively")
        this.addRequiredOption("i", "input-name", true, "All files inside the specified 'input-directory' folder with the same name are processed.")
        this.addRequiredOption("o", "output-name", true, "The file name of the generated file. The file is saved next to the input file (in the same directory)")
    }
}

fun parseCLArgs(args: Array<String>, options: Options): CommandLine {
    try {
        return DefaultParser().parse(options, args)
    } catch (ex: ParseException) {
        println(ex.message)
        printCLHelp(options)
        exitProcess(1)
    }
}

fun printCLHelp(options: Options) {
    HelpFormatter().printHelp("extract", "Extracts the computed CSS style for HTML tables. Only the first table is processed.", options, "")
}