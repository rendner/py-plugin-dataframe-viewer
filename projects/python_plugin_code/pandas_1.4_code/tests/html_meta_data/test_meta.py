#  Copyright 2021 cms.rendner (Daniel Schmidt)
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
import pandas as pd
import pytest

from plugin_code.patched_styler import PatchedStyler
from tests.helpers.asserts.table_extractor import TableExtractor, OffsetIndexTranslator, SequenceIndexTranslator

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize(
    "styler, expected_row_indexer_class, expected_col_indexer_class", [
        (df.style, OffsetIndexTranslator, OffsetIndexTranslator),
        (df.style.hide(axis="index").hide(axis="columns"), OffsetIndexTranslator, OffsetIndexTranslator),
        (df.style.hide(axis="columns", subset=['col_1']), OffsetIndexTranslator, SequenceIndexTranslator),
        (df.style.hide(axis="index", subset=[0, 1]), SequenceIndexTranslator, OffsetIndexTranslator),
        (df.style.hide(axis="index", subset=[0, 1]).hide(axis="columns", subset=['col_1']), SequenceIndexTranslator, SequenceIndexTranslator)
    ])
def test_correct_indexer(styler, expected_row_indexer_class, expected_col_indexer_class):
    table_extractor = TableExtractor()
    patched_styler = PatchedStyler(styler)

    rows_per_chunk = 2
    cols_per_chunk = 2

    table_structure = patched_styler.get_table_structure()
    for ri in range(0, table_structure.visible_rows_count, rows_per_chunk):
        for ci in range(0, table_structure.visible_columns_count, cols_per_chunk):
            chunk_html = patched_styler.render_chunk(
                ri,
                ci,
                ri + rows_per_chunk,
                ci + cols_per_chunk
            )

            table_extractor.extract(chunk_html)

            assert isinstance(table_extractor.ctx.col_indexer, expected_col_indexer_class)
            assert isinstance(table_extractor.ctx.row_indexer, expected_row_indexer_class)
