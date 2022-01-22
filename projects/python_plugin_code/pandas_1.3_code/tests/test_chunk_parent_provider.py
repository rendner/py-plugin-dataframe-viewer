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
from typing import Union

import pytest
from pandas import DataFrame, Series

from plugin_code.chunk_parent_provider import ChunkParentProvider

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


# axis = {0 or ‘index’, 1 or ‘columns’, None}
@pytest.mark.parametrize("axis, chunk, expected_chunk_parent", [
    (None, df.iloc[[0]], df),
    (0, df.iloc[1:3, 0], df["col_0"]),
    (1, df.iloc[0, 1:3], df.iloc[0]),
    ("index", df.iloc[1:3, 0], df["col_0"]),
    ("columns", df.iloc[0, 1:3], df.iloc[0]),
])
def test_correct_chunk_parent_is_provided(axis, chunk: Union[DataFrame, Series],
                                          expected_chunk_parent: Union[DataFrame, Series]):
    chunk_parent: Union[DataFrame, Series, None] = None

    def style_func(chunk, **kwargs):
        nonlocal chunk_parent
        chunk_parent = kwargs.get('chunk_parent', None)

    ChunkParentProvider(style_func, axis, df)(chunk)

    assert expected_chunk_parent.equals(chunk_parent)


def test_raises_a_key_error_if_parent_cant_be_resolved():
    chunk = df.iloc[1:3, 0]
    other_df = DataFrame([[0, 0], [0, 0]])

    msg = "'col_0'"
    with pytest.raises(KeyError, match=msg):
        ChunkParentProvider(lambda x: x, "index", other_df)(chunk)

