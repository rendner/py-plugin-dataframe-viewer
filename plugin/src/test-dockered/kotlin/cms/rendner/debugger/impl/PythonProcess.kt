/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
package cms.rendner.debugger.impl

import java.io.*

class PythonProcess(
    private val lineSeparator: String,
    private val printOutput: Boolean = false,
    private val printInput: Boolean = false,
) {
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter
    private lateinit var process: Process

    private var singleCharArray = CharArray(1)
    private val stringBuilder = StringBuilder()

    /*
    To detect BdbQuit error message:

    Traceback (most recent call last):
      File "/usr/local/lib/python3.11/bdb.py", line 94, in trace_dispatch
        return self.dispatch_return(frame, arg)
               ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      File "/usr/local/lib/python3.11/bdb.py", line 156, in dispatch_return
        if self.quitting: raise BdbQuit
                          ^^^^^^^^^^^^^
    bdb.BdbQuit

     */
    private val BDP_QUIT_REGEX = Regex("Traceback \\(most recent call last\\):([\\S,\\s])+bdb\\.BdbQuit\n")

    /**
     * @param processArgs the arguments containing the program to start and its arguments.
     */
    fun start(processArgs: List<String>) {
        process = ProcessBuilder(processArgs).start()

        reader = process.inputStream.bufferedReader()
        writer = process.outputStream.bufferedWriter()
    }

    /**
     * Writes a line to the process.
     * The line-termination character [lineSeparator] is automatically added.
     *
     * @param input the string to write, not including any line-termination characters
     */
    fun writeLine(input: String) {
        if (printInput) {
            println("PythonProcess::writeLine:$lineSeparator$input$lineSeparator")
        }
        writer.write("$input$lineSeparator")
        writer.flush()
    }

    /**
     * Tells whether data is available and can be read from the process.
     */
    fun canRead(): Boolean {
        return reader.ready()
    }

    /**
     * Reads the available output of the process. Returns immediately an empty list if no output is available.
     * @return A list of Strings containing the lines, not including [lineSeparator] character.
     */
    fun readLinesNonBlocking(): List<String> {
        /*
        The blocking "readLine" method of "reader" can't be used here.
        The python debugger, if waiting for input, prints a prompt "(Pdb) " without a new line.
        Using "readLine" would block forever.
         */

        stringBuilder.clear()
        while (reader.ready() && reader.read(singleCharArray, 0, 1) != -1) {
            stringBuilder.append(singleCharArray)
        }
        if (printOutput && stringBuilder.isNotEmpty()) {
            println("PythonProcess::readLinesNonBlocking:$lineSeparator$stringBuilder$lineSeparator")
        }
        return stringBuilder.toString()
            .split(lineSeparator)
            .filterNot { it.isEmpty() }
    }

    /**
     * Closes the input and output stream of the process and destroys the underlying process.
     */
    fun cleanup() {
        process.errorStream.bufferedReader().use { errReader ->
            // If pdb quit command was sent, a traceback like that occurs because there is nothing
            // to catch the BdbQuit exception raised when the debugger quits.
            errReader.readText().replace(BDP_QUIT_REGEX, "").let {
                if (it.isNotEmpty()) print(it)
            }
        }
        closeSilently(reader)
        closeSilently(writer)
        process.destroy()
    }

    /**
     * Tests whether the process represented by this [Process] is alive.
     *
     * @return `true` if the process represented by this [Process] object has not yet terminated.
     */
    fun isAlive(): Boolean {
        return process.isAlive
    }

    private fun closeSilently(c: Closeable) {
        try {
            c.close()
        } catch (ignore: Exception) {

        }
    }
}