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

# == copy after here ==
class TableStructure:
    def __init__(self,
                 rows_count: int,
                 columns_count: int,
                 visible_rows_count: int,
                 visible_columns_count: int,
                 row_levels_count: int,
                 column_levels_count: int,
                 hide_row_header: bool,
                 hide_column_header: bool):
        self.rows_count = rows_count
        self.columns_count = columns_count
        self.visible_rows_count = visible_rows_count
        self.visible_columns_count = visible_columns_count
        self.row_levels_count = row_levels_count
        self.column_levels_count = column_levels_count
        self.hide_row_header = hide_row_header
        self.hide_column_header = hide_column_header
