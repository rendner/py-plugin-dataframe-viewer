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
package cms.rendner.intellij.dataframe.viewer.models.chunked.events

import cms.rendner.intellij.dataframe.viewer.models.ITableDataModel
import javax.swing.event.TableModelEvent

@Suppress("RemoveRedundantQualifierName")
class ChunkTableModelEvent private constructor(
    source: ITableDataModel,
    firstRow: Int,
    lastRow: Int,
    column: Int,
    type: Int,
    val payload: UpdatePayload
) : TableModelEvent(source, firstRow, lastRow, column, type) {

    enum class UpdateType(val value: Int) {
        HEADER_LABELS(TableModelEvent.DELETE - 1),
        VALUES(TableModelEvent.DELETE - 2)
    }

    data class UpdatePayload(
        val firstRow: Int,
        val lastRow: Int,
        val firstColumn: Int,
        val lastColumn: Int,
        val type: UpdateType
    )

    /*
    TableModelEvent(source);                            //  The data, ie. all rows changed
    TableModelEvent(source, HEADER_ROW);                //  Structure change, reallocate TableColumns
    TableModelEvent(source, 1);                         //  Row 1 changed
    TableModelEvent(source, 3, 6);                      //  Rows 3 to 6 inclusive changed
    TableModelEvent(source, 2, 2, 6);                   //  Cell at (2, 6) changed
    TableModelEvent(source, 3, 6, ALL_COLUMNS, INSERT); // Rows (3, 6) were inserted
    TableModelEvent(source, 3, 6, ALL_COLUMNS, DELETE); // Rows (3, 6) were deleted
     */

    companion object {

        fun createHeaderLabelsChanged(
            source: ITableDataModel,
            firstColumn: Int,
            lastColumn: Int
        ): TableModelEvent {
            /*
               TableModelEvent example for notifying the table that a single cell was changed, cell at (2, 6):
               TableModelEvent(source, 2, 2, 6);

               Use non-existing coordinates for the event to deliver the payload.
            */
            return ChunkTableModelEvent(
                source,
                Int.MIN_VALUE, // don't use -1 this will reset the selection (JTable::tableChanged)
                Int.MIN_VALUE, // don't use Int.MAX_VALUE this will reset the selection (JTable::tableChanged)
                Int.MIN_VALUE,
                TableModelEvent.UPDATE,
                UpdatePayload(
                    0,
                    0,
                    firstColumn,
                    lastColumn,
                    UpdateType.HEADER_LABELS
                )
            )
        }

        fun createValuesChanged(
            source: ITableDataModel,
            firstRow: Int,
            lastRow: Int,
            firstColumn: Int,
            lastColumn: Int
        ): TableModelEvent {
            return ChunkTableModelEvent(
                source,
                firstRow,
                lastRow,
                TableModelEvent.ALL_COLUMNS,
                TableModelEvent.UPDATE,
                UpdatePayload(
                    firstRow,
                    lastRow,
                    firstColumn,
                    lastColumn,
                    UpdateType.VALUES
                )
            )
        }
    }
}