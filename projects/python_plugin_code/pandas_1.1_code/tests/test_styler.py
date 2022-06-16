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
from pandas import DataFrame

df_non_unique_cols = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "a"]
)
df_non_unique_idx = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "c"],
    index=["x", "y", "x"]
)
df_non_unique_cols_idx = pd.DataFrame(
    data=np.arange(1, 10).reshape(3, 3),
    columns=["a", "b", "a"],
    index=["x", "y", "x"]
)

'''
These tests are vital. If they fail, the patchers can no longer work.
'''


@pytest.mark.parametrize("my_df", [
    df_non_unique_cols,
    df_non_unique_idx,
    df_non_unique_cols_idx,
])
def test_raise_non_unique_key_error(my_df: DataFrame):
    msg = "style is not supported for non-unique indices."
    with pytest.raises(ValueError, match=msg):
        # noinspection PyStatementEffect
        my_df.style
