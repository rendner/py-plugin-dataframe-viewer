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
import numpy as np
import pytest
from pandas import DataFrame, IndexSlice, MultiIndex

from tests.helpers.asserts.assert_styler_sorting import create_and_assert_patched_styler_sorting
from tests.helpers.custom_styler_functions import highlight_even_numbers

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
multi_df = DataFrame(np.arange(0, 36).reshape(6, 6), index=midx, columns=midx)


def test_sort_values_before_styling_breaks_styling():
    # values are already in the right order - no sorting applied
    styler_asc = df.sort_values(by=['col_0'], ascending=True).style.highlight_min(subset=IndexSlice[2:4])
    assert "background-color: yellow;" in styler_asc.render()

    # values get sorted - there will be no continues rows with the indices 2,3,4
    # -> df.sort_values(by=['col_0'], ascending=False).loc[IndexSlice[2:4]] is a DataFrame with 0 rows
    # -> sorting can't be done before the styling
    styler_asc = df.sort_values(by=['col_0'], ascending=False).style.highlight_min(subset=IndexSlice[2:4])
    assert "background-color: yellow;" not in styler_asc.render()


@pytest.mark.parametrize(
    "sort_by, ascending", [
        ([0], [False]),
        ([0, 2], [True, False]),
        ([0, 2], [True, True]),
        ([0, 2], [False, False]),
        ([4, 2, 3], [False, True, False]),
    ]
)
def test_sorting_by_multiple_columns(sort_by, ascending):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.highlight_min(),
        2,
        2,
        sort_by,
        ascending,
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
@pytest.mark.parametrize("sort_by, ascending", [([0], [False])])
def test_sorting_with_multi_df(axis, sort_by, ascending):
    create_and_assert_patched_styler_sorting(
        multi_df,
        lambda styler: styler.highlight_min(axis=axis, color="yellow").highlight_max(axis=axis, color="red"),
        2,
        2,
        sort_by,
        ascending,
    )


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
    IndexSlice[2:4],
    IndexSlice[2:4, ["col_2", "col_3"]],
    None,
])
def test_sorting_with_subset(subset):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.apply(highlight_even_numbers, subset=subset),
        2,
        2,
        [0],
        [False],
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
def test_sorting_with_apply(axis):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.apply(highlight_even_numbers, axis=axis),
        2,
        2,
        [0],
        [False],
    )


def test_sorting_with_applymap():
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.applymap(highlight_even_numbers),
        2,
        2,
        [0],
        [False],
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
def test_sorting_with_background_gradient(axis):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.background_gradient(axis=axis),
        2,
        2,
        [0],
        [False],
    )


def test_sorting_with_format():
    def formatter(v):
        return v if v < 10 else f"+{v}"

    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.format(formatter=formatter),
        2,
        2,
        [0],
        [False],
        lambda styler: styler.format(formatter=formatter),
    )


def test_hide_index_row_of_a_styled_subset_should_not_break_sorting():
    create_and_assert_patched_styler_sorting(
        df,
        # hide a row of the subset of highlight_max before
        lambda styler: styler.hide_index(subset=IndexSlice[3:3]).highlight_max(subset=IndexSlice[2:4]),
        2,
        2,
        [0],
        [False],
        lambda styler: styler.hide_index(subset=IndexSlice[3:3]),
    )

    create_and_assert_patched_styler_sorting(
        df,
        # hide a row of the subset of highlight_max afterwards
        lambda styler: styler.highlight_max(subset=IndexSlice[2:4]).hide_index(subset=IndexSlice[3:3]),
        2,
        2,
        [0],
        [False],
        lambda styler: styler.hide_index(subset=IndexSlice[3:3]),
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
def test_sorting_with_highlight_between(axis):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.highlight_between(axis=axis),
        2,
        2,
        [0],
        [False],
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
def test_sorting_with_highlight_max(axis):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.highlight_max(axis=axis),
        2,
        2,
        [0],
        [False],
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
def test_sorting_with_highlight_min(axis):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.highlight_min(axis=axis),
        2,
        2,
        [0],
        [False],
    )


def test_sorting_with_highlight_null():
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.highlight_null(),
        2,
        2,
        [0],
        [False],
    )


@pytest.mark.parametrize("axis", [None, "index", "columns"])
def test_sorting_with_highlight_quantile(axis):
    create_and_assert_patched_styler_sorting(
        df,
        lambda styler: styler.highlight_quantile(axis=axis),
        2,
        2,
        [0],
        [False],
    )
