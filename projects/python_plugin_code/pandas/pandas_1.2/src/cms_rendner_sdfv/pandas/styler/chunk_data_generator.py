#  Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
from typing import List, Union, Dict

from cms_rendner_sdfv.base.table_source import AbstractChunkDataGenerator
from cms_rendner_sdfv.base.types import Region, ChunkData, Cell
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


class ChunkDataGenerator(AbstractChunkDataGenerator):
    def __init__(self, styler_context: PatchedStylerContext):
        super().__init__(styler_context.visible_frame)
        self.__styler_context: PatchedStylerContext = styler_context

    def generate(self,
                 region: Region = None,
                 with_row_headers: bool = True,
                 ) -> ChunkData:
        formatter = ValueFormatter()
        styled_chunk = self.__styler_context.compute_styled_chunk(region)

        if styled_chunk.row_labels_hidden:
            with_row_headers = False

        col_range = range(styled_chunk.region.cols)
        cells: List[List[Cell]] = []
        index_labels: List[List[str]] = []
        for r in range(styled_chunk.region.rows):
            if with_row_headers:
                index_labels.append([formatter.format_index(lbl) for lbl in styled_chunk.row_labels_at(r)])

            row_cells = []
            cells.append(row_cells)
            for c in col_range:
                css = styled_chunk.cell_css_at(r, c)
                row_cells.append(
                    Cell(
                        value=formatter.format_cell(styled_chunk.cell_value_at(r, c)),
                        css=self._css_to_dict(css),
                    ),
                )

        return ChunkData(index_labels=index_labels, cells=cells)

    @staticmethod
    def _css_to_dict(css: List[str]) -> Union[None, Dict[str, str]]:
        if not css:
            return None

        css_dict = {}
        for keyval in css:
            if keyval:
                k, v = [x.strip() for x in keyval.split(':')]
                if k and v:
                    css_dict[k] = v

        return css_dict if css_dict else None
