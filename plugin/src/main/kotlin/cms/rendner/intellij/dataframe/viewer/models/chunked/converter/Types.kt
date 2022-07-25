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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter

// https://github.com/pandas-dev/pandas/blob/1.2.x/pandas/io/formats/style.py#L266-L272
object HeaderCssClasses {
    const val ROW_HEADING_CLASS = "row_heading"
    const val COL_HEADING_CLASS = "col_heading"
    const val INDEX_NAME_CLASS = "index_name"

    const val DATA_CLASS = "data"
    const val BLANK_CLASS = "blank"
    // don't add/use the def of "BLANK_VALUE" because it has changed between 1.2.x and 1.3.x
}