/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.notifications

import cms.rendner.intellij.dataframe.viewer.DataFrameViewerIcons
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.ProblemReason
import cms.rendner.intellij.dataframe.viewer.python.bridge.ValidationStrategyType
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.lang.Integer.min
import javax.swing.JOptionPane

/**
 * Notification about a chunk-validation-result.
 *
 * @param region the validated region in the pandas DataFrame
 * @param validationStrategy the used validation strategy
 * @param problems the detected problems
 */
class ChunkValidationProblemNotification(
    region: ChunkRegion,
    validationStrategy: ValidationStrategyType,
    problems: List<ValidationProblem>,
) : AbstractBalloonNotification(
    "Possible incompatible styling function found",
    "Found ${problems.size} problem(s)\nin $region",
    NotificationType.WARNING,
) {

    init {
        icon = DataFrameViewerIcons.LOGO_16
        addAction(ShowValidationReportAction(region, validationStrategy, problems))
        addAction(CopyToClipboardAction(region, validationStrategy, problems))
    }

    private class CopyToClipboardAction(
        private val region: ChunkRegion,
        private val validationStrategy: ValidationStrategyType,
        private val problems: List<ValidationProblem>,
    ) : AnAction("Copy To Clipboard"), DumbAware {
        override fun actionPerformed(p0: AnActionEvent) {
            val message = ClipboardReportGenerator().createReport(region, validationStrategy, problems)
            val selection = StringSelection(message)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }

    private class ShowValidationReportAction(
        private val region: ChunkRegion,
        private val validationStrategy: ValidationStrategyType,
        private val problems: List<ValidationProblem>,
    ) : AnAction("Show Report"), DumbAware {

        override fun actionPerformed(event: AnActionEvent) {
            showHtmlMessageDialog(
                event.project,
                HTMLReportGenerator().createReport(region, validationStrategy, problems),
                "Chunk Validation Report",
                JOptionPane.INFORMATION_MESSAGE,
            ) { messageScrollPane ->
                messageScrollPane.preferredSize = Dimension(
                    min(800, messageScrollPane.preferredSize.width),
                    250,
                )
            }
        }
    }

    private abstract class AbstractReportGenerator {
        fun createReport(
            region: ChunkRegion,
            validationStrategy: ValidationStrategyType,
            problems: List<ValidationProblem>,
        ): String {
            return stringify(region, validationStrategy, groupByReason(problems))
        }

        protected abstract fun stringify(
            region: ChunkRegion,
            validationStrategy: ValidationStrategyType,
            sections: List<Section>,
        ): String

        protected fun extractReportableValues(
            problem: ValidationProblem,
            valueTransformer: ((key: ReportableValue, value: Any) -> String) = { _, value -> value.toString() },
        ): Map<ReportableValue, String> {
            return mutableMapOf<ReportableValue, String>().apply {
                val addValue = { key: ReportableValue, value: Any -> this[key] = valueTransformer(key, value) }
                if (problem.message.isNotEmpty()) {
                    addValue(ReportableValue.MESSAGE, problem.message)
                }
                problem.funcInfo.let {
                    addValue(ReportableValue.INDEX, it.index.toString())
                    addValue(ReportableValue.FUNC_NAME, it.resolvedName)
                    if (it.qname != it.resolvedName) {
                        addValue(ReportableValue.FUNC_QNAME, it.qname)
                    }
                    if (it.isPandasBuiltin) {
                        addValue(ReportableValue.IS_PANDAS_BUILTIN, true)
                        addValue(ReportableValue.IS_SUPPORTED_BY_PLUGIN, it.isSupported)
                    } else {
                        addValue(ReportableValue.IS_ARG_CHUNK_PARENT, it.isChunkParentRequested)
                    }
                    if (it.isApply) {
                        // only the apply-method has an axis param
                        addValue(ReportableValue.ARG_AXIS, it.axis)
                    }
                }
            }
        }

        private fun groupByReason(problems: List<ValidationProblem>): List<Section> {
            val warnings = Section("Warnings", "Exception during validation")
            val errors = Section("Errors", "Styling function is not chunk aware (compared results didn't match)")

            problems.forEach {
                when (it.reason) {
                    ProblemReason.NOT_EQUAL -> errors.entries.add(it)
                    ProblemReason.EXCEPTION -> warnings.entries.add(it)
                }
            }

            return mutableListOf<Section>().apply {
                if (errors.entries.isNotEmpty()) {
                    add(errors)
                }
                if (warnings.entries.isNotEmpty()) {
                    add(warnings)
                }
            }
        }

        enum class ReportableValue(val label: String) {
            MESSAGE("message"),
            INDEX("index"),
            FUNC_NAME("func-name"),
            FUNC_QNAME("func-qname"),
            IS_PANDAS_BUILTIN("pandasBuiltin"),
            IS_SUPPORTED_BY_PLUGIN("isSupported"),
            IS_ARG_CHUNK_PARENT("arg-chunkParent"),
            ARG_AXIS("arg-axis"),
        }

        data class Section(
            val title: String,
            val reason: String,
            val entries: MutableList<ValidationProblem> = mutableListOf()
        )
    }

    private class ClipboardReportGenerator : AbstractReportGenerator() {
        override fun stringify(
            region: ChunkRegion,
            validationStrategy: ValidationStrategyType,
            sections: List<Section>,
        ): String {
            return StringBuilder().apply {
                appendHeaderInfo(this, region, validationStrategy)
                sections.forEach { appendSection(this, it) }
            }.toString()
        }

        private fun appendHeaderInfo(
            sb: StringBuilder,
            region: ChunkRegion,
            validationStrategy: ValidationStrategyType
        ) {
            with(sb) {
                append("Possible incompatible styling function(s) found.")
                appendLine()
                appendLine()
                appendRow("region", region)
                appendRow("validationStrategy", validationStrategy)
                appendLine()
            }
        }

        private fun appendSection(sb: StringBuilder, section: Section) {
            with(sb) {
                appendLine("${section.title}:")
                section.entries.forEach { appendSectionEntry(sb, it, section.reason) }
                appendLine()
            }
        }

        private fun appendSectionEntry(
            sb: StringBuilder,
            entry: ValidationProblem,
            reason: String,
        ) {
            with(sb) {
                appendRow("reason", reason)
                extractReportableValues(entry).forEach { (key, value) -> appendRow(key.label, value) }
                appendLine()
            }
        }

        private fun StringBuilder.appendRow(label: String, value: Any) {
            appendLine(String.format("\t%-20s\t%s", "$label:", value))
        }
    }

    private class HTMLReportGenerator : AbstractReportGenerator() {
        override fun stringify(
            region: ChunkRegion,
            validationStrategy: ValidationStrategyType,
            sections: List<Section>,
        ): String {
            return StringBuilder().apply {
                append("<html>")
                appendHeaderInfo(this, region, validationStrategy)
                sections.forEach { appendSection(this, it) }
                append("</html>")
            }.toString()
        }

        private fun appendHeaderInfo(
            sb: StringBuilder,
            region: ChunkRegion,
            validationStrategy: ValidationStrategyType
        ) {
            with(sb) {
                append("Possible incompatible styling function(s) found.")
                append("<br/><br/>")
                append("<table>")
                appendTableRow("region", region)
                appendTableRow("validationStrategy", validationStrategy)
                append("</table>")
                append("<br/>")
            }
        }

        private fun appendSection(sb: StringBuilder, section: Section) {
            with(sb) {
                append("<h2>${section.title}:</h2>")
                append("<ol>")
                section.entries.forEach {
                    append("<li>")
                    appendSectionEntry(sb, it, section.reason)
                    append("</li>")
                }
                append("</ol>")
            }
        }

        private fun appendSectionEntry(
            sb: StringBuilder,
            entry: ValidationProblem,
            reason: String
        ) {
            val labeledValues = extractReportableValues(entry) { key, value ->
                val result = value.toString()
                when (key) {
                    ReportableValue.MESSAGE -> StringUtil.escapeXmlEntities(result)
                    ReportableValue.FUNC_NAME -> StringUtil.escapeXmlEntities(result)
                    ReportableValue.FUNC_QNAME -> StringUtil.escapeXmlEntities(result)
                    else -> result
                }
            }
            with(sb) {
                append("<table>")
                appendTableRow("reason", reason)
                labeledValues.forEach { (key, value) -> appendTableRow(key.label, value) }
                append("</table><br/>")
            }
        }

        private fun StringBuilder.appendTableRow(label: String, value: Any) {
            append("<tr><td>$label:</td><td width='10' /><td>$value</td></tr>")
        }
    }
}