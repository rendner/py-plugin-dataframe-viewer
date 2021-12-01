/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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