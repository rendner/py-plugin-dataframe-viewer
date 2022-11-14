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
from plugin_code.html_props_table_builder import HTMLPropsTable, HTMLPropsTableBuilder
from plugin_code.patched_styler_context import Region

# == copy after here ==


class HTMLPropsTableGenerator:
    def __init__(self, props_generator: HTMLPropsGenerator):
        self.__props_generator: HTMLPropsGenerator = props_generator

    def internal_compute_unpatched_table(self) -> HTMLPropsTable:
        table_builder = HTMLPropsTableBuilder()
        table_builder.append_props(
            html_props=self.__props_generator.internal_compute_unpatched_props(),
            target_row_offset=0,
            is_part_of_first_rows_in_chunk=True,
            is_part_of_first_cols_in_chunk=True,
        )
        return table_builder.build()

    def compute_chunk_table(self,
                            region: Region,
                            exclude_row_header: bool = False,
                            ) -> HTMLPropsTable:
        html_props = self.__props_generator.compute_chunk_props(
            region=region,
            exclude_row_header=exclude_row_header,
            translate_indices=False,
        )

        table_builder = HTMLPropsTableBuilder()
        table_builder.append_props(
            html_props=html_props,
            target_row_offset=0,
            is_part_of_first_rows_in_chunk=True,
            is_part_of_first_cols_in_chunk=True,
        )
        return table_builder.build()

    def compute_table_from_chunks(self,
                                  region: Region,
                                  rows_per_chunk: int,
                                  cols_per_chunk: int,
                                  ) -> HTMLPropsTable:
        table_builder = HTMLPropsTableBuilder()

        for chunk_region in region.iterate_chunkwise(rows_per_chunk, cols_per_chunk):
            chunk_html_props = self.__props_generator.compute_chunk_props(
                region=Region(
                    region.first_row + chunk_region.first_row,
                    region.first_col + chunk_region.first_col,
                    chunk_region.rows,
                    chunk_region.cols,
                ),
                exclude_row_header=chunk_region.first_col > 0,
                translate_indices=False,
            )

            table_builder.append_props(
                html_props=chunk_html_props,
                target_row_offset=chunk_region.first_row,
                is_part_of_first_rows_in_chunk=chunk_region.first_row == 0,
                is_part_of_first_cols_in_chunk=chunk_region.first_col == 0,
            )

        return table_builder.build()
