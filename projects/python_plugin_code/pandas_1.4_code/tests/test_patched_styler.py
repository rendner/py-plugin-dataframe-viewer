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

from plugin_code.apply_fallback_patch import ApplyFallbackPatch
from plugin_code.apply_map_fallback_patch import ApplyMapFallbackPatch
from plugin_code.background_gradient_patch import BackgroundGradientPatch
from plugin_code.highlight_between_patch import HighlightBetweenPatch

from plugin_code.highlight_extrema_patch import HighlightExtremaPatch
from plugin_code.patched_styler import PatchedStyler

df = pd.DataFrame.from_dict({("A", "col_0"): [0, 1, 2, 3, np.nan]})


@pytest.mark.parametrize(
    "styler, mapped_class", [
        (df.style.background_gradient(), BackgroundGradientPatch),
        (df.style.highlight_between(), HighlightBetweenPatch),
        (df.style.highlight_min(), HighlightExtremaPatch),
        (df.style.highlight_max(), HighlightExtremaPatch),
        (df.style.highlight_null(), ApplyFallbackPatch),
        (df.style.highlight_quantile(), HighlightBetweenPatch),
        (df.style.set_properties(), ApplyMapFallbackPatch),
        (df.style.text_gradient(), BackgroundGradientPatch),
    ])
def test_should_handle_builtin_styler(styler, mapped_class):
    assert_patch(PatchedStyler(styler), mapped_class)


def assert_patch(patched_styler: PatchedStyler, classinfo):
    assert len(patched_styler._PatchedStyler__patched_styles) == 1
    assert isinstance(patched_styler._PatchedStyler__patched_styles[0], classinfo)


def test_table_structure_hide_row_header():
    styler = df.style.hide(axis="index")
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.hide_row_header is True
    assert ts.hide_column_header is False


def test_table_structure_hide_column_header():
    styler = df.style.hide(axis="columns")
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.hide_column_header is True
    assert ts.hide_row_header is False


def test_table_structure_columns_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.columns_count == 1


def test_table_structure_rows_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.rows_count == 5


def test_table_structure_visible_columns_count():
    styler = df.style.hide(axis="columns", subset=df.columns)
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.visible_columns_count == 0
    assert ts.columns_count == 1


def test_table_structure_visible_rows_count():
    styler = df.style.hide(axis="index", subset=df.index)
    ts = PatchedStyler(styler).get_table_structure()
    assert ts.visible_rows_count == 0
    assert ts.rows_count == 5


def test_table_structure_column_level_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.column_levels_count == 2


def test_table_structure_row_level_count():
    ts = PatchedStyler(df.style).get_table_structure()
    assert ts.row_levels_count == 1
