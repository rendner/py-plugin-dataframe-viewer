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
from typing import List, Any

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext
from cms_rendner_sdfv.pandas.shared.visible_frame import VisibleFrame


class Chunk:
    def __init__(self, visible_frame: VisibleFrame, region: Region):
        self.__visible_frame = visible_frame
        self.region = region

    def cell_value_at(self, row: int, col: int):
        return self.__visible_frame.cell_value_at(
            self.region.first_row + row,
            self.region.first_col + col,
        )

    def col_labels_at(self, col: int) -> List[Any]:
        labels = self.__visible_frame.column_at(self.region.first_col + col)
        if not isinstance(labels, tuple):
            labels = [labels]
        return labels

    def row_labels_at(self, row: int) -> List[Any]:
        labels = self.__visible_frame.index_at(self.region.first_row + row)
        if not isinstance(labels, tuple):
            labels = [labels]
        return labels


class FrameContext(PandasTableSourceContext):
    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        # local import to resolve cyclic import
        from cms_rendner_sdfv.pandas.frame.table_frame_generator import TableFrameGenerator
        return TableFrameGenerator(self)

    def get_chunk(self, region: Region) -> Chunk:
        return Chunk(self.visible_frame, self.visible_frame.region.get_bounded_region(region))
