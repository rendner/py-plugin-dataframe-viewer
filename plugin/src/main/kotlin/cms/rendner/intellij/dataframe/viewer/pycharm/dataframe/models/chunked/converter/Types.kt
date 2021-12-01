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
package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter

// https://github.com/pandas-dev/pandas/blob/1.2.x/pandas/io/formats/style.py#L266-L272
enum class HeaderCssClasses(val value: String) {
    ROW_HEADING_CLASS("row_heading"),
    COL_HEADING_CLASS("col_heading"),
    INDEX_NAME_CLASS("index_name"),

    DATA_CLASS("data"),
    BLANK_CLASS("blank"),
    // don't use the def of "BLANK_VALUE" because it has changed between 1.2.x and 1.3.x
}

interface IndexTranslator {
    fun translate(index: Int): Int
}
class SequenceIndex(private val sequence: IntArray): IndexTranslator {
    override fun translate(index: Int) = sequence[index]
}
class OffsetIndex(private val offset: Int): IndexTranslator {
    override fun translate(index: Int) = offset + index
}
class NOOPTranslator: IndexTranslator {
    override fun translate(index: Int) = index
}
class RowColTranslator(
    val row: IndexTranslator,
    val column: IndexTranslator
)