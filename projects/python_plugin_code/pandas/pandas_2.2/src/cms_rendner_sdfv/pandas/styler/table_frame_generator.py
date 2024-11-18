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
from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self, styler_context: PatchedStylerContext):
        super().__init__(styler_context.visible_frame)
        self.__styler_context: PatchedStylerContext = styler_context

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 ) -> TableFrame:
        formatter = ValueFormatter()
        styled_chunk = self.__styler_context.compute_styled_chunk(region)

        if styled_chunk.row_labels_hidden:
            exclude_row_header = True

        cells: list[list[TableFrameCell]] = []
        index_labels: list[list[str]] = []
        for r in range(styled_chunk.region.rows):
            row_cells = []
            cells.append(row_cells)
            for c in range(styled_chunk.region.cols):
                if c == 0 and not exclude_row_header:
                    row_labels = styled_chunk.row_labels_at(r)
                    if row_labels:
                        index_labels.append([formatter.format_index(lbl) for lbl in row_labels])
                css = styled_chunk.cell_css_at(r, c)
                row_cells.append(
                    TableFrameCell(
                        value=formatter.format_cell(styled_chunk.cell_value_at(r, c)),
                        css=None if not css or css is None else {k: v for k, v in css},
                    ),
                )

        return TableFrame(index_labels=index_labels, cells=cells)
