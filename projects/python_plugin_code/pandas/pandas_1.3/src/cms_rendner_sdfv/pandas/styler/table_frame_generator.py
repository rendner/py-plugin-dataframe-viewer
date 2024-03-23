#  Copyright 2021-2024 cms.rendner (Daniel Schmidt)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
from typing import List, Union

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext, StyledChunk


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self, styler_context: PatchedStylerContext):
        super().__init__(styler_context.visible_frame)
        self.__styler_context: PatchedStylerContext = styler_context

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        formatter = ValueFormatter()
        styled_chunk = self.__styler_context.compute_styled_chunk(region)

        if styled_chunk.row_labels_hidden:
            exclude_row_header = True
        if styled_chunk.column_labels_hidden:
            exclude_col_header = True

        cells: List[List[TableFrameCell]] = []
        columns: List[TableFrameColumn] = []
        index_labels: List[List[str]] = []
        for r in range(0, styled_chunk.region.rows):
            row_cells = []
            cells.append(row_cells)
            for c in range(0, styled_chunk.region.cols):
                if c == 0 and not exclude_row_header:
                    row_labels = styled_chunk.row_labels_at(r)
                    if row_labels:
                        index_labels.append([formatter.format_index(lbl) for lbl in row_labels])
                if r == 0 and not exclude_col_header:
                    col_labels = styled_chunk.col_labels_at(c)
                    if col_labels:
                        info = self._visible_frame.get_column_info(styled_chunk.region.first_col + c)
                        columns.append(
                            TableFrameColumn(
                                dtype=str(info.dtype),
                                labels=[formatter.format_column(lbl) for lbl in col_labels],
                                describe=info.describe(),
                            ),
                        )

                css = styled_chunk.cell_css_at(r, c)
                row_cells.append(
                    TableFrameCell(
                        value=formatter.format_cell(styled_chunk.cell_value_at(r, c)),
                        css=None if not css or css is None else {k: v for k, v in css},
                    ),
                )

        return TableFrame(
            index_labels=index_labels,
            columns=columns,
            cells=cells,
            legend=None if exclude_col_header and exclude_row_header
            else self._extract_legend_label(formatter, styled_chunk),
        )

    def _extract_legend_label(self, formatter: ValueFormatter, chunk: StyledChunk) -> Union[None, TableFrameLegend]:
        if chunk.row_labels_hidden or chunk.column_labels_hidden:
            return None

        index_legend = [
            formatter.format_index(n)
            for i, n in enumerate(self._visible_frame.index_names)
            if n is not None
        ]

        column_legend = [
            formatter.format_index(n)
            for i, n in enumerate(self._visible_frame.column_names)
            if n is not None
        ]
        return TableFrameLegend(index=index_legend, column=column_legend) if index_legend or column_legend else None
