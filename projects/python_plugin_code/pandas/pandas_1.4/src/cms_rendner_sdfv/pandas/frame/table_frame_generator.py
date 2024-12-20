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
from typing import List

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.frame_value_formatter import FrameValueFormatter


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self, context: FrameContext):
        super().__init__(context.visible_frame)
        self.__context: FrameContext = context

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 ) -> TableFrame:
        chunk = self.__context.get_chunk(region)
        formatter = FrameValueFormatter()

        col_range = range(chunk.region.cols)
        cells: List[List[TableFrameCell]] = []
        index_labels: List[List[str]] = []

        for r in range(chunk.region.rows):
            if not exclude_row_header:
                index_labels.append([formatter.format_index(lbl) for lbl in chunk.row_labels_at(r)])
            cells.append(
                [TableFrameCell(value=formatter.format_cell(chunk.cell_value_at(r, c))) for c in col_range]
            )

        return TableFrame(index_labels=index_labels, cells=cells)
