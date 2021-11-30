package cms.rendner.intellij.dataframe.viewer.pycharm.actions

import cms.rendner.intellij.dataframe.viewer.pycharm.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.ListPartsIterator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.ValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.exporter.TestCaseExportData
import cms.rendner.intellij.dataframe.viewer.pycharm.exporter.TestCaseExporter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.jetbrains.python.debugger.PyDebugValue
import java.nio.file.Path
import java.nio.file.Paths

class ExportDataFrameTestDataAction : AnAction(), DumbAware {

    private val exportDir = System.getProperty("cms.rendner.dataframe.renderer.export.test.data.dir")?.let {
        Paths.get(it)
    }
    private var isEnabled =
        System.getProperty("cms.rendner.dataframe.renderer.enable.test.data.export", "false") == "true"

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabled = isEnabled
        event.presentation.isVisible = exportDir != null && event.project != null && getExportDataValue(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (exportDir == null) return
        val exportDataValue = getExportDataValue(event) ?: return
        val project: Project = XDebuggerTree.getTree(event.dataContext)!!.project
        isEnabled = false
        ProgressManager.getInstance().run(MyExportTask(project, exportDir, exportDataValue) {
            ApplicationManager.getApplication().invokeLater {
                isEnabled = true
            }
        })
    }

    private fun getExportDataValue(e: AnActionEvent): PyDebugValue? {
        val nodes = XDebuggerTreeActionBase.getSelectedNodes(e.dataContext)
        if (nodes.size == 1) {
            val container = nodes.first().valueContainer
            if (container is PyDebugValue) {
                if (container.qualifiedType == PythonQualifiedTypes.Dict.value && container.name == "export_test_data") {
                    return container
                }
            }
        }
        return null
    }

    private data class ExportData(val testCases: PyDebugValue, val pandasMajorMinorVersion: String)

    private class MyExportTask(
        project: Project,
        private val baseExportDir: Path,
        private val exportDataValue: PyDebugValue,
        private val onFinally: () -> Unit
    ) : Task.Backgroundable(project, "Exporting test cases", true) {

        override fun run(progressIndicator: ProgressIndicator) {
            var failed = false
            try {
                val evaluator = ValueEvaluator(exportDataValue.frameAccessor)
                val exportData = convertExportValue(exportDataValue, evaluator)
                val exportDir = baseExportDir.resolve("pandas${exportData.pandasMajorMinorVersion}")
                println("exportDir: $exportDir")
                val testCaseExporter = TestCaseExporter(exportDir)
                val partsIterator = ListPartsIterator(exportData.testCases)
                val currentThread = Thread.currentThread()

                while (partsIterator.hasNext()) {

                    val part = partsIterator.next()
                    if (currentThread.isInterrupted) break

                    for (exportTestCaseValue in part) {
                        try {
                            testCaseExporter.export(convertTestCaseValue(exportTestCaseValue, evaluator))
                            if (currentThread.isInterrupted) break
                        } catch (ex: Exception) {
                            println("export failed: $ex")
                            failed = true
                            throw ex
                        }
                    }
                }
            } finally {
                println("export ${if(failed) "failed" else "done"}")
                onFinally()
            }
        }

        private fun convertExportValue(exportDataDict: PyDebugValue, evaluator: IValueEvaluator): ExportData {
            exportDataDict.evaluationExpression.let {
                val testCases = evaluator.evaluate("$it['test_cases']")
                val pandasVersion = evaluator.evaluate("$it['pandas_version']")
                val versionParts = pandasVersion.value!!.split(".")
                val majorMinor = "${versionParts[0]}.${versionParts[1]}"
                return ExportData(testCases, majorMinor)
            }
        }

        private fun convertTestCaseValue(exportTestCaseDict: PyDebugValue, evaluator: IValueEvaluator): TestCaseExportData {
            return exportTestCaseDict.evaluationExpression.let {
                val styler = evaluator.evaluate("$it['styler']")
                val chunkSize = evaluator.evaluate("str($it['chunk_size'][0]) + ':' + str($it['chunk_size'][1])")
                val exportDir = evaluator.evaluate("$it['export_dir']")
                TestCaseExportData(
                    styler,
                    chunkSize.value!!.split(":").let { parts -> ChunkSize(parts[0].toInt(), parts[1].toInt()) },
                    exportDir.value!!
                )
            }
        }
    }
}