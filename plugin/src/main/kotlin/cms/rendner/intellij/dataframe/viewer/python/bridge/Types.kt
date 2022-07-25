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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.openapi.Disposable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class PandasVersion(val major: Int, val minor: Int, val patch: String = "") {
    companion object {
        fun fromString(value: String): PandasVersion {
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
 * Provides the Python plugin code for a specific pandas version.
 */
class PythonCodeProvider(
    val version: PandasVersion,
    private val codeResourcePath: String,
) {
    fun getCode(): String {
        return PythonCodeProvider::class.java.getResource(codeResourcePath)!!.readText()
    }
}

/**
 * Interface to evaluate values of the Python class "PatchedStyler".
 */
interface IPyPatchedStylerRef : Disposable {
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
     * @param firstRow index of the first row of the region to validate
     * @param firstColumn index of the first column of the region to validate
     * @param numberOfRows number of rows in the region to validate
     * @param numberOfColumns number of columns in the region to validate
     * @param validationStrategy the validation strategy to use, in case of [ValidationStrategyType.DISABLED]
     *  the method immediately returns an empty list without evaluation the specified region.
     * @return the found problems or an empty list of no problems could be found by the used validation strategy.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateValidateStyleFunctions(
        firstRow: Int,
        firstColumn: Int,
        numberOfRows: Int,
        numberOfColumns: Int,
        validationStrategy: ValidationStrategyType,
    ): List<StyleFunctionValidationProblem>

    /**
     * Calls the "set_sort_criteria" method of the Python class "PatchedStyler".
     *
     * @param byColumnIndex list of column indices to be sorted, <code>null</code> for no sorting.
     * @param ascending the sort order for each specified column, must match the length of [byColumnIndex]
     */
    @Throws(EvaluateException::class)
    fun evaluateSetSortCriteria(byColumnIndex: List<Int>? = null, ascending: List<Boolean>? = null)

    /**
     * Calls the "render_chunk" method of the Python class "PatchedStyler".
     *
     * @param firstRow index of the first row of the chunk
     * @param firstColumn index of the first column of the chunk
     * @param numberOfRows number of rows in the chunk
     * @param numberOfColumns number of columns in the chunk
     * @param excludeRowHeader hides the row headers before creating the html string
     * @param excludeColumnHeader hides the column headers before creating the html string
     * @return returns the html string returned by pandas "Styler::to_html"
     * @throws EvaluateException in case the evaluation fails.
     *
     * [pandas-docs - Styler.to_html](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.to_html.html)
     */
    @Throws(EvaluateException::class)
    fun evaluateRenderChunk(
        firstRow: Int,
        firstColumn: Int,
        numberOfRows: Int,
        numberOfColumns: Int,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean
    ): String

    /**
     * Calls the "compute_chunk_html_props_table" method of the Python class "PatchedStyler".
     *
     * @param firstRow index of the first row of the chunk
     * @param firstColumn index of the first column of the chunk
     * @param numberOfRows number of rows in the chunk
     * @param numberOfColumns number of columns in the chunk
     * @param excludeRowHeader hides the row headers before creating the html string
     * @param excludeColumnHeader hides the column headers before creating the html string
     * @return returns the html props of the chunk.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateComputeChunkHTMLPropsTable(
        firstRow: Int,
        firstColumn: Int,
        numberOfRows: Int,
        numberOfColumns: Int,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean
    ): HTMLPropsTable

    /**
     * Calls the "compute_unpatched_html_props_table" method of the Python class "PatchedStyler".
     * This method is only used to dump small DataFrames to generate test data and during integration tests.
     *
     * @return returns the unpatched html props of the underling DataFrame.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateComputeUnpatchedHTMLPropsTable(): HTMLPropsTable

    /**
     * Calls the "render_unpatched" method of the Python class "PatchedStyler".
     * This method is only used to dump small DataFrames to generate test data and during integration tests.
     *
     * @return returns the html string returned by pandas "Styler::to_html"
     * @throws EvaluateException in case the evaluation fails.
     *
     * [pandas-docs - Styler.to_html](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.to_html.html)
     */
    @Throws(EvaluateException::class)
    fun evaluateRenderUnpatched(): String
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
    @SerialName("th") TH,
    @SerialName("td") TD,
}

@Serializable
enum class RowElementKind {
    /**
     * An empty table header, used for leading empty cells to align columns.
     */
    @SerialName("blank") BLANK,

    /**
     * A table header which contains the name of an index.
     */
    @SerialName("index_name") INDEX_NAME,

    /**
     * A table header which contains the name of a column.
     */
    @SerialName("col_heading") COL_HEADING,

    /**
     * A table header which contains the name of a row header.
     */
    @SerialName("row_heading") ROW_HEADING,

    /**
     * Placeholder value for all other kinds.
     */
    @SerialName("") UNKNOWN,
}