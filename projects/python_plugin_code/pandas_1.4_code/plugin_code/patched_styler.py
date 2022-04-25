#  Copyright 2022 cms.rendner (Daniel Schmidt)
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
from plugin_code.html_props_generator import HTMLPropsGenerator
from plugin_code.html_props_validator import HTMLPropsValidator
from plugin_code.table_structure import TableStructure
from plugin_code.todos_patcher import TodosPatcher

# == copy after here ==
import numpy as np
from pandas import DataFrame
from pandas.io.formats.style import Styler


class PatchedStyler:

    def __init__(self, styler: Styler):
        self.__styler: Styler = styler
        self.__visible_data: DataFrame = self.__get_visible_data(styler)
        self.__html_props_generator = HTMLPropsGenerator(self.__get_visible_data(styler), styler)

    def create_html_props_validator(self) -> HTMLPropsValidator:
        return HTMLPropsValidator(self.__visible_data, self.__styler)

    def render_chunk(
            self,
            first_row: int,
            first_column: int,
            last_row: int,
            last_column: int,
            exclude_row_header: bool = False,
            exclude_column_header: bool = False
    ) -> str:
        html_props = self.__html_props_generator.generate_props_for_chunk(
            first_row=first_row,
            first_column=first_column,
            last_row=last_row,
            last_column=last_column,
            exclude_row_header=exclude_row_header,
            exclude_column_header=exclude_column_header,
        )
        return self.__html_props_generator.create_html(html_props)

    def render_unpatched(self) -> str:
        # This method deliberately does not use the "html_props_generator" but the original
        # "Styler::to_html" method to create the html string.
        #
        # Method is only used in unit tests or to create test data for the plugin
        # therefore it is save to change potential configured values
        self.__styler.uuid = ''
        self.__styler.uuid_len = 0
        self.__styler.cell_ids = False
        return self.__styler.to_html(
            encoding="utf-8",
            doctype_html=True,
            sparse_columns=False,
            sparse_index=False,
        )

    def get_table_structure(self) -> TableStructure:
        return TableStructure(
            rows_count=len(self.__visible_data.index),
            columns_count=len(self.__visible_data.columns),
            row_levels_count=self.__visible_data.index.nlevels - self.__styler.hide_index_.count(True),
            column_levels_count=self.__visible_data.columns.nlevels - self.__styler.hide_columns_.count(True),
            hide_row_header=all(self.__styler.hide_index_),
            hide_column_header=all(self.__styler.hide_columns_)
        )

    @staticmethod
    def __get_visible_data(styler: Styler) -> DataFrame:
        if len(styler.hidden_rows) == 0 and len(styler.hidden_columns) == 0:
            return styler.data
        else:
            visible_indices = np.delete(styler.index.get_indexer_for(styler.index), styler.hidden_rows)
            visible_columns = np.delete(styler.columns.get_indexer_for(styler.columns), styler.hidden_columns)
            return styler.data.iloc[visible_indices, visible_columns]
