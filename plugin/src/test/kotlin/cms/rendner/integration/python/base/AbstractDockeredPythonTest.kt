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
package cms.rendner.integration.python.base

import cms.rendner.integration.python.debugger.DockeredPipenvEnvironment
import cms.rendner.integration.python.debugger.DockeredPythonEvalDebugger
import cms.rendner.integration.python.debugger.EvalOnlyFrameAccessor
import cms.rendner.integration.python.debugger.PythonEvalDebugger
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.ValueEvaluator
import org.junit.jupiter.api.*
import java.util.concurrent.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal open class AbstractDockeredPythonTest {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val debugger = DockeredPythonEvalDebugger()
    private val pipenvEnvironment: DockeredPipenvEnvironment = DockeredPipenvEnvironment.labelOf(
        System.getProperty("cms.rendner.dataframe.renderer.integration.test.pipenv.environment")
    )

    @BeforeAll
    protected fun initializeDebuggerContainer() {
        debugger.startContainer()
    }

    @AfterAll
    protected fun destroyDebuggerContainer() {
        debugger.destroyContainer()
        executorService.shutdown()
        executorService.awaitTermination(5, TimeUnit.SECONDS)
    }

    @BeforeEach
    protected fun resetDebugger() {
        debugger.reset()
    }

    @AfterEach
    protected fun shutdownDebugger() {
        debugger.shutdown()
    }

    protected fun runWithPythonDebugger(
        block: (debugger: PythonEvalDebugger) -> Unit,
    ) {
        executorService.submit {
            //debugger.startWithSourceFile("/usr/src/app/enter_debugger_example.py", pipenvEnvironment)
            debugger.startWithCodeSnippet("breakpoint()", pipenvEnvironment)
        }
        block(debugger)
    }

    protected fun createValueEvaluator(debugger: PythonEvalDebugger): IValueEvaluator {
        return ValueEvaluator(EvalOnlyFrameAccessor(debugger))
    }
}