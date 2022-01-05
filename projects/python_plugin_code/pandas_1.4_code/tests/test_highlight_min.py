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
import numpy as np
import pytest

from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler
from tests.helpers.checkers import not_required_pandas_version

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("color, props", [(None, "font-weight: bold;"), ("pink", None)])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_chunked(axis, subset, color, props, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.highlight_min(axis=axis, subset=subset, color=color, props=props),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.highlight_min(axis=axis, subset=subset),
        2,
        2
    )


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
])
def test_frame_can_handle_reducing_subset(subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.highlight_min(axis=None, subset=subset),
        2,
        2
    )


@pytest.mark.skipif(not_required_pandas_version(">=1.3.2"), reason="at least pandas-1.3.2 required")
@pytest.mark.parametrize("axis", [None, 0, 1])
def test_highlight_min_nulls(axis):
    # GH 42750
    create_and_assert_patched_styler(
        pd.DataFrame({"a": [pd.NA, -1, None], "b": [np.nan, -1, 1]}),
        # replace pd.NA values with '' otherwise the are rendered as <NA> and interpreted as html tag
        lambda styler: styler.format(na_rep='').highlight_min(axis=axis),
        2,
        2
    )
