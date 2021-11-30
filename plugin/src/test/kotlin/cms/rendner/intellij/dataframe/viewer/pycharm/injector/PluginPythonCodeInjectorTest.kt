package cms.rendner.intellij.dataframe.viewer.pycharm.injector

import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.common.pythonGetPandasVersion
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.common.pythonStyledDataFrameViewerBridgeClass
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.python.debugger.ArrayChunk
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.debugger.PyReferrersLoader
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
internal class PluginPythonCodeInjectorTest {

    @Mock
    private lateinit var evaluator: IValueEvaluator

    @Test
    fun throwsExceptionIfEvaluatorThrowsException() {
        Mockito.`when`(evaluator.evaluate(Mockito.anyString())).thenThrow(EvaluateException::class.java)

        val injector = PluginPythonCodeInjector()

        assertThatExceptionOfType(InjectException::class.java).isThrownBy {
            injector.ensurePluginCodeIsInjected(evaluator)
        }
    }

    @Test
    fun injectsCodeForPandas_1_1() {
        assertInjectedPandasCode(
            "1.1.x",
            cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor1.PandasCodeProvider()
        )
    }

    @Test
    fun injectsCodeForPandas_1_2() {
        assertInjectedPandasCode(
            "1.2.x",
            cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor2.PandasCodeProvider()
        )
    }

    @Test
    fun injectsCodeForPandas_1_3() {
        assertInjectedPandasCode(
            "1.3.x",
            cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor3.PandasCodeProvider()
        )
    }

    @Test
    fun throwsInjectionForUnsupportedPandasVersion() {
        val injector = PluginPythonCodeInjector()

        Mockito.`when`(evaluator.evaluate("${injector.getBridgeExpr()}.check()"))
            .thenReturn(createPyDebugValue("False"))
        Mockito.`when`(evaluator.evaluate(pythonGetPandasVersion)).thenReturn(createPyDebugValue("1.4.0"))

        assertThatThrownBy { injector.ensurePluginCodeIsInjected(evaluator) }
            .isExactlyInstanceOf(InjectException::class.java)
            .hasMessage("Unsupported PandasVersion(major=1, minor=4, patch=0).")
    }

    @Test
    private fun assertInjectedPandasCode(pandasVersion: String, expectedCodeProvider: IPandasCodeProvider) {
        val injector = PluginPythonCodeInjector()

        Mockito.`when`(evaluator.evaluate("${injector.getBridgeExpr()}.check()"))
            .thenReturn(createPyDebugValue("False"))
        Mockito.`when`(evaluator.evaluate(pythonGetPandasVersion)).thenReturn(createPyDebugValue(pandasVersion))

        injector.ensurePluginCodeIsInjected(evaluator)

        Mockito.verify(evaluator).execute(ArgumentMatchers.contains(expectedCodeProvider.getCode()))
        Mockito.verify(evaluator).execute(ArgumentMatchers.contains(pythonStyledDataFrameViewerBridgeClass))
    }

    private fun createPyDebugValue(value: String): PyDebugValue {
        return PyDebugValue(
            "name",
            "type",
            "typeQualifier",
            value,
            false,
            null,
            false,
            false,
            false,
            FakeFrameAccessor()
        )
    }

    private class FakeFrameAccessor : PyFrameAccessor {
        override fun evaluate(p0: String?, p1: Boolean, p2: Boolean): PyDebugValue {
            TODO("Not yet implemented")
        }

        override fun loadFrame(): XValueChildrenList? {
            TODO("Not yet implemented")
        }

        override fun loadVariable(p0: PyDebugValue?): XValueChildrenList {
            TODO("Not yet implemented")
        }

        override fun changeVariable(p0: PyDebugValue?, p1: String?) {
            TODO("Not yet implemented")
        }

        override fun getReferrersLoader(): PyReferrersLoader? {
            TODO("Not yet implemented")
        }

        override fun getArrayItems(p0: PyDebugValue?, p1: Int, p2: Int, p3: Int, p4: Int, p5: String?): ArrayChunk {
            TODO("Not yet implemented")
        }

        override fun getSourcePositionForName(p0: String?, p1: String?): XSourcePosition? {
            TODO("Not yet implemented")
        }

        override fun getSourcePositionForType(p0: String?): XSourcePosition? {
            TODO("Not yet implemented")
        }
    }
}