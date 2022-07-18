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
from pandas import DataFrame

from plugin_code.patched_styler_context import PatchedStylerContext

df = DataFrame.from_dict({
    "col_0": [4, 4, 4, 1, 4],
    "col_1": [1, 4, 4, 1, 2],
})


def test_previous_sort_criteria_does_not_affect_later_sort_criteria():
    ctx = PatchedStylerContext(df.style)
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_first_sort = ctx.get_visible_frame().index

    ctx.set_sort_criteria(sort_by_column_index=[0, 1], sort_ascending=[True, True])
    # assert to ensure test setup is correct
    index_in_between = ctx.get_visible_frame().index
    assert not all(index_after_first_sort == index_in_between)

    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_last_sort = ctx.get_visible_frame().index
    assert all(index_after_first_sort == index_after_last_sort)
