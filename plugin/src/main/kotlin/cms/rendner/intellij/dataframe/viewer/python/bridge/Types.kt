/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class PandasVersion(val major: Int, val minor: Int, val rest: String = "") {
    companion object {
        /**
         * Creates a version instance from a pandas version string.
         *
         * pandas version is defined as described in pep-440:
         * Version Identification and Dependency Specification
         * https://peps.python.org/pep-0440/
         */
        fun fromString(value: String): PandasVersion {
            // versions can be:
            // - '1.2.0'
            // - '1.4.0.dev0+1574.g46ddb8ef88'
            // - '2.0.0rc0'
            // this is a very naive way to extract the required info (but OK)
            val parts = value.split(".")
            return PandasVersion(
                parts[0].toInt(),
                parts[1].toInt(),
                value.substring(parts[0].length + parts[1].length + 2)
            )
        }
    }
}

/**
 * Interface to evaluate values of the Python class "PatchedStyler".
 */
interface IPyPatchedStylerRef {
    /**
     * Calls the "get_table_structure" method of the Python class "PatchedStyler".
     * The returned result contains information about the visible rows and columns of a DataFrame.
     *
     * @return structural information about the pandas DataFrame.
     * @throws EvaluateException in case the evaluation fails.
     *
     * [pandas-docs - Styler.to_html](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.to_html.html)
     */
    @Throws(EvaluateException::class)
    fun evaluateTableStructure(): TableStructure

    /**
     * Calls the "get_style_function_details" method of the Python class "PatchedStyler".
     *
     * The result contains details about each styling function registered via "Styler::apply" and "Styler::applymap".
     *
     * @return list of details about registered styling functions.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateStyleFunctionDetails(): List<StyleFunctionDetails>

    /**
     * Calls the "validate_style_functions" method of the Python class "PatchedStyler".
     *
     * The result contains the found validation problems.
     *
     * @param chunk the region of the data to validate
     * @param validationStrategy the validation strategy to use, in case of [ValidationStrategyType.DISABLED]
     *  the method immediately returns an empty list without evaluation the specified region.
     * @return the found problems or an empty list of no problems could be found by the used validation strategy.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateValidateStyleFunctions(
        chunk: ChunkRegion,
        validationStrategy: ValidationStrategyType,
    ): List<StyleFunctionValidationProblem>

    /**
     * Calls the "set_sort_criteria" method of the Python class "PatchedStyler".
     *
     * @param sortCriteria the new sort criteria, <code>null</code> for no sorting.
     */
    @Throws(EvaluateException::class)
    fun evaluateSetSortCriteria(sortCriteria: SortCriteria?)

    /**
     * Calls the "compute_chunk_html_props_table" method of the Python class "PatchedStyler".
     *
     * @param chunk the region of the data to evaluate
     * @param excludeRowHeader hides the row headers before creating the html string
     * @param excludeColumnHeader hides the column headers before creating the html string
     * @return returns the html props of the chunk.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateComputeChunkHTMLPropsTable(
        chunk: ChunkRegion,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean
    ): HTMLPropsTable

    /**
     * Calls the "internal_compute_unpatched_html_props_table" method of the Python class "PatchedStyler".
     * This method is only used to dump small DataFrames to generate test data and during integration tests.
     *
     * @return returns the unpatched html props of the underling DataFrame.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateComputeUnpatchedHTMLPropsTable(): HTMLPropsTable

    /**
     * Calls the "get_org_indices_of_visible_columns" method of the Python class "PatchedStyler".
     */
    @Throws(EvaluateException::class)
    fun evaluateGetOrgIndicesOfVisibleColumns(partStart: Int, maxColumns: Int): List<Int>
}

/**
 * The HTML properties of a DataFrame or chunk.
 *
 * @param head the columns of a DataFrame. In case of multiindex the list contains more than one row of columns.
 * @param body the values and row headers of a DataFrame.
 */
@Serializable
data class HTMLPropsTable(val head: List<List<HTMLPropsRowElement>>, val body: List<List<HTMLPropsRowElement>>)

/**
 * Table header or table data element.
 *
 * @param type indicates if a header or data.
 * @param kind describes what kind of element. Mostly used to describe the type of header.
 * @param displayValue the value displayed by the element.
 * @param cssProps the styling of the value.
 */
@Serializable
data class HTMLPropsRowElement(
    val type: RowElementType,
    val kind: RowElementKind,
    @SerialName("display_value") val displayValue: String,
    @SerialName("css_props") val cssProps: Map<String, String>?,
)

@Serializable
enum class RowElementType {
    @SerialName("th")
    TH,
    @SerialName("td")
    TD,
}

@Serializable
enum class RowElementKind {
    /**
     * An empty table header, used for leading empty cells to align columns.
     */
    @SerialName("blank")
    BLANK,

    /**
     * A table header which contains the name of an index.
     */
    @SerialName("index_name")
    INDEX_NAME,

    /**
     * A table header which contains the name of a column.
     */
    @SerialName("col_heading")
    COL_HEADING,

    /**
     * A table header which contains the name of a row header.
     */
    @SerialName("row_heading")
    ROW_HEADING,

    /**
     * Placeholder value for all other kinds.
     */
    @SerialName("")
    UNKNOWN,
}