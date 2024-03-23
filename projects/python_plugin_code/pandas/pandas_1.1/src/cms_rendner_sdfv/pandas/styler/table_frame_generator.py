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
from typing import Callable, List, Optional, Union, Dict

from pandas import Series
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self,
                 styler_context: PatchedStylerContext,
                 todos_filter: Optional[Callable[[StylerTodo], bool]] = None,
                 ):
        super().__init__(styler_context.visible_frame)
        self.__styler_context: PatchedStylerContext = styler_context
        self.__todos_filter: Optional[Callable[[StylerTodo], bool]] = todos_filter

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        org_styler = self.__styler_context.get_styler()

        if self._exclude_headers:
            exclude_row_header = True
            exclude_col_header = True
        else:
            if org_styler.hidden_index:
                exclude_row_header = True

        # -- Compute styling
        # The plugin only renders the visible (non-hidden cols/rows) of the styler DataFrame
        # therefore the chunk is created from the visible data.
        chunk = self._visible_frame.get_chunk(region)
        chunk_df = self._visible_frame.to_frame(chunk)

        # The apply/map params are patched to not operate outside the chunk bounds.
        chunk_aware_todos = self.__styler_context.create_patched_todos(chunk_df, self.__todos_filter)

        # Create a new styler which refers to the same DataFrame to not pollute original styler
        copy = org_styler.data.style
        # assign patched todos
        copy._todo = chunk_aware_todos
        # Compute the styling for the chunk by operating on the original DataFrame.
        # The computed styler contains only entries for the cells of the chunk,
        # this is ensured by the patched todos.
        computed_styler = copy._compute()

        formatter = ValueFormatter()
        translate_pos = self._visible_frame.create_to_source_frame_cell_coordinates_translator(chunk)
        chunk_df.style.render()
        cells: List[List[TableFrameCell]] = []
        columns: List[TableFrameColumn] = []
        index_labels: List[List[str]] = []
        for ri, row_tuple in enumerate(chunk_df.itertuples(index=False, name=None)):
            row_cells = []
            cells.append(row_cells)
            for ci in range(0, chunk.region.cols):
                if ci == 0:
                    if not exclude_row_header:
                        row_name = chunk_df.index[ri]
                        if isinstance(row_name, tuple):
                            row_name = [formatter.format_index(x) for x in row_name]
                        else:
                            row_name = [formatter.format_index(row_name)]
                        if row_name:
                            index_labels.append(row_name)
                if ri == 0:
                    if not exclude_col_header:
                        col_series: Series = chunk_df.iloc[:, ci]
                        col_name = col_series.name
                        if isinstance(col_name, tuple):
                            col_name = [formatter.format_index(n) for n in col_name]
                        else:
                            col_name = [formatter.format_index(col_name)]
                        if col_name:
                            info = self._visible_frame.get_column_info(chunk.region.first_col + ci)
                            columns.append(
                                TableFrameColumn(dtype=str(info.dtype), labels=col_name, describe=info.describe()),
                            )
                org_cell_pos = translate_pos((ri, ci))
                css = computed_styler.ctx[org_cell_pos]
                row_cells.append(
                    TableFrameCell(
                        # Use value from "row_tuple" as the styler does when converting to html.
                        # "chunk_df.iat[ri, ci]" can't be used, because float32 values are not converted
                        # with the configured precision (in pandas 1.3, works as expected in pandas 1.4).
                        # To stay safe, use the same approach as the styler does.
                        value=formatter.format_cell(computed_styler._display_funcs[org_cell_pos](row_tuple[ci])),
                        css=self._css_to_dict(css),
                    ),
                )

        return TableFrame(
            index_labels=index_labels,
            columns=columns,
            cells=cells,
            legend=None if exclude_col_header and exclude_row_header else self._extract_legend_label(
                formatter,
                org_styler,
            ),
        )

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

    def _extract_legend_label(self, formatter: ValueFormatter, org_styler: Styler) -> Union[None, TableFrameLegend]:
        if org_styler.hidden_index:
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
