/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.network.CefRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class CSSStyleComputer(
    private val jsQueryCode: String,
    sourceDirectory: Path,
    inputFileName: String,
    private val outputFileName: String,
    private val allDoneCallback: () -> Unit,
) : CefMessageRouterHandlerAdapter(), CefLoadHandler {
    private var currentIndex: Int = -1
    private val files: List<File>

    init {
        files = sourceDirectory.toFile().walkTopDown().filter { it.isFile and (it.name == inputFileName) }.toList()
        println("found ${files.size} in ${sourceDirectory.toUri()}")
    }

    override fun onQuery(
        browser: CefBrowser,
        frame: CefFrame,
        query_id: Long,
        request: String,
        persistent: Boolean,
        callback: CefQueryCallback
    ): Boolean {
        val file = files[currentIndex]
        val outputPath = Path.of(file.parent, outputFileName)
        println("\twriting export: $outputPath")
        Files.newBufferedWriter(outputPath).use {
            it.write(request)
        }
        loadNextFile(browser)
        return true
    }

    override fun onLoadingStateChange(
        browser: CefBrowser,
        isLoading: Boolean,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
    }

    override fun onLoadStart(browser: CefBrowser, frame: CefFrame, type: CefRequest.TransitionType) {
    }

    override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if(currentIndex >= 0) {
            browser.executeJavaScript(jsQueryCode, "", 0)
        } else {
            loadNextFile(browser)
        }
    }

    override fun onLoadError(
        browser: CefBrowser,
        frame: CefFrame,
        errorCode: CefLoadHandler.ErrorCode,
        errorText: String,
        failedUrl: String
    ) {
        println("Couldn't load ${browser.url}: $errorText")
    }

    private fun loadNextFile(browser: CefBrowser) {
        if (currentIndex + 1 < files.size) {
            currentIndex += 1
            browser.loadURL(files[currentIndex].toURI().toString())
        } else {
            allDoneCallback()
        }
    }
}