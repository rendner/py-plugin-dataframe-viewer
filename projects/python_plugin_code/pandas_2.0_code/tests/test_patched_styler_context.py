#  Copyright 2023 cms.rendner (Daniel Schmidt)
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

from plugin_code.patched_styler_context import PatchedStylerContext, FilterCriteria

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [1, 2, 3, 4, 5],
    "col_2": [2, 3, 4, 5, 6],
    "col_3": [3, 4, 5, 6, 7],
    "col_4": [4, 5, 6, 7, 8],
})


def test_previous_sort_criteria_does_not_affect_later_sort_criteria():
    ctx = PatchedStylerContext(df.style)
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_first_sort = ctx.get_visible_frame().index

    ctx.set_sort_criteria(sort_by_column_index=[0, 1], sort_ascending=[False, False])
    # assert to ensure test setup is correct
    index_in_between = ctx.get_visible_frame().index
    assert list(index_after_first_sort) != list(index_in_between)

    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[True])
    index_after_last_sort = ctx.get_visible_frame().index
    assert list(index_after_first_sort) == list(index_after_last_sort)


def test_get_org_indices_of_visible_columns():
    ctx = PatchedStylerContext(df.style)

    actual = ctx.get_org_indices_of_visible_columns(0, 2)
    assert list(actual) == [0, 1]

    actual = ctx.get_org_indices_of_visible_columns(2, 2)
    assert list(actual) == [2, 3]

    actual = ctx.get_org_indices_of_visible_columns(4, 2)
    assert list(actual) == [4]


def test_get_org_indices_of_visible_columns_with_filter():
    # include only two columns of org df - but change the order of these columns
    fc = FilterCriteria.from_frame(df[['col_4', 'col_1']])
    ctx = PatchedStylerContext(df.style, fc)

    actual = ctx.get_org_indices_of_visible_columns(0, 2)
    assert list(actual) == [1, 4]
