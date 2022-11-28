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
import numpy as np
import pandas as pd
import pytest

from tests.helpers.assert_style_func_parameters import assert_style_func_parameters
from tests.helpers.asserts.assert_styler import create_and_assert_patched_styler

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("vmin, vmax", [(None, None), (5, 10)])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_chunked(axis, subset, vmin, vmax, rows_per_chunk, cols_per_chunk):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.background_gradient(axis=axis, subset=subset, vmin=vmin, vmax=vmax),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
def test_can_handle_axis_values(axis, subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.background_gradient(axis=axis, subset=subset),
        2,
        2
    )


@pytest.mark.parametrize("axis", [None, 0, 1])
@pytest.mark.parametrize("subset", [None, pd.IndexSlice[2:3, ["col_2", "col_3"]]])
@pytest.mark.parametrize("vmin, vmax", [(None, None), (5, 10)])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        (len(df.index), len(df.columns))  # single chunk
    ])
def test_gmap_chunked(axis, subset, rows_per_chunk, vmin, vmax, cols_per_chunk):
    gmap = df if axis is None else df['col_1']
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.background_gradient(axis=axis, subset=subset, vmin=vmin, vmax=vmax, gmap=gmap),
        rows_per_chunk,
        cols_per_chunk
    )


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
])
def test_frame_can_handle_reducing_subset(subset):
    create_and_assert_patched_styler(
        df,
        lambda styler: styler.background_gradient(axis=None, subset=subset),
        2,
        2
    )


# https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.io.formats.style.Styler.background_gradient.html
pandas_api_example_df = pd.DataFrame(
    columns=["City", "Temp (c)", "Rain (mm)", "Wind (m/s)"],
    data=[["Stockholm", 21.6, 5.0, 3.2],
          ["Oslo", 22.4, 13.3, 3.1],
          ["Copenhagen", 24.5, 0.0, 6.7]]
)


def test_pandas_api_example_1():
    create_and_assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.background_gradient(axis=0, gmap=pandas_api_example_df['Temp (c)'], cmap='YlOrRd'),
        2,
        2
    )


def test_pandas_api_example_2():
    gmap = np.array([[1, 2, 3], [2, 3, 4], [3, 4, 5]])
    create_and_assert_patched_styler(
        pandas_api_example_df,
        lambda styler: styler.background_gradient(axis=None, gmap=gmap, cmap='YlOrRd',
                                                  subset=['Temp (c)', 'Rain (mm)', 'Wind (m/s)']),
        2,
        2
    )


# source: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/tests/io/formats/style/test_matplotlib.py#L169-L183
@pytest.mark.parametrize(
    "axis, gmap",
    [
        (0, [1, 2]),
        (1, [1, 2]),
        (None, np.array([[2, 1], [1, 2]]))
    ],
)
def test_pandas_test_example_background_gradient_gmap_array(axis, gmap):
    # tests when gmap is given as a sequence and converted to ndarray
    own_df = pd.DataFrame([[0, 0], [0, 0]])
    create_and_assert_patched_styler(
        own_df,
        lambda styler: styler.background_gradient(axis=axis, gmap=gmap),
        1,
        1
    )


@pytest.mark.parametrize(
    "axis, gmap", [
        (0, [1, 2, 3]),
        (1, [1, 2]),
        (None, np.array([[1, 2], [1, 2]]))
    ]
)
def test_pandas_test_example_background_gradient_gmap_array_raises(axis, gmap):
    # test when gmap as converted ndarray is bad shape
    own_df = pd.DataFrame([[0, 0, 0], [0, 0, 0]])
    msg = "supplied 'gmap' is not correct shape"
    with pytest.raises(ValueError, match=msg):
        create_and_assert_patched_styler(
            own_df,
            lambda styler: styler.background_gradient(axis=axis, gmap=gmap),
            2,
            2
        )


def test_for_new_parameters():
    assert_style_func_parameters(
        df.style.background_gradient,
        ['axis', 'subset', 'cmap', 'low', 'high', 'text_color_threshold', 'vmin', 'vmax', 'gmap']
    )
