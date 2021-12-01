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
import example.simple.MainFrame
import org.cef.CefApp
import org.cef.browser.CefMessageRouter
import java.awt.event.WindowEvent
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val options = createCLOptions()
    val cmdLine = parseCLArgs(args, options)

    if (cmdLine.hasOption("help")) {
        printCLHelp(options)
    } else {
        val inputDir = cmdLine.getOptionValue("d")
        val inputFileName = cmdLine.getOptionValue("i")
        val outputFileName = cmdLine.getOptionValue("o")

        createComputingBrowserFrame(
            Path.of(inputDir),
            inputFileName,
            outputFileName,
        )
    }
}

fun createComputingBrowserFrame(inputDir: Path, inputFileName: String, outputFileName: String): MainFrame? {
    val cefArgs = emptyArray<String>()
    if (!CefApp.startup(cefArgs)) {
        println("'CefApp.startup' failed!")
        return null
    }

    // The file MainFrame was copied from:
    // https://raw.githubusercontent.com/JetBrains/jcef/master/java_tests/tests/simple/MainFrame.java
    // And a "getClient" method was added to add additional configurations.
    //
    // Windowed rendering mode is used by default. If you want to test OSR mode set |useOsr| to true and recompile.
    val useOsr = false
    val url = "http://localhost"
    return MainFrame(cefArgs, url, useOsr, false).also {

        val allDoneCallback = {
            println("All files processed. Exit application.")
            SwingUtilities.invokeLater {
                it.dispatchEvent(WindowEvent(it, WindowEvent.WINDOW_CLOSING))
                exitProcess(0)
            }
        }
        val styleComputer = CSSStyleComputer(
            createJSQueryCode(),
            inputDir,
            inputFileName,
            outputFileName,
            allDoneCallback,
        )

        val msgRouterConfig = CefMessageRouter.CefMessageRouterConfig()
        val msgRouter = CefMessageRouter.create(msgRouterConfig)
        msgRouter.addHandler(styleComputer, true)

        it.getClient().apply {
            this.addMessageRouter(msgRouter)
            this.addLoadHandler(styleComputer)
        }
    }
}