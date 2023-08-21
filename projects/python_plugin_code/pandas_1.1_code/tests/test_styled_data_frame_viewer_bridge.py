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
import pandas as pd
import pytest

from plugin_code.create_fingerprint import create_fingerprint
from plugin_code.patched_styler import PatchedStyler
from plugin_code.styled_data_frame_viewer_bridge import \
    StyledDataFrameViewerBridge, CreatePatchedStylerConfig, CreatePatchedStylerFailure

df_dict = {
    "col_0": [4, 4, 4, 1, 4],
    "col_1": [1, 4, 4, 1, 2],
}

df = pd.DataFrame.from_dict(df_dict)


def test_create_patched_styler_for_df():
    assert isinstance(StyledDataFrameViewerBridge.create_patched_styler(df), PatchedStyler)


def test_create_patched_styler_for_styler():
    assert isinstance(StyledDataFrameViewerBridge.create_patched_styler(df.style), PatchedStyler)


def test_create_patched_styler_for_dict_orient_columns():
    ps = StyledDataFrameViewerBridge.create_patched_styler(df_dict)
    assert isinstance(ps, PatchedStyler)
    assert list(ps.internal_get_context().get_visible_frame().columns) == list(df_dict.keys())


def test_create_patched_styler_for_dict_orient_index():
    ps = StyledDataFrameViewerBridge.create_patched_styler(
        df_dict,
        CreatePatchedStylerConfig(data_source_to_frame_hint="DictKeysAsRows"),
    )
    assert isinstance(ps, PatchedStyler)
    assert list(ps.internal_get_context().get_visible_frame().columns) == [0, 1, 2, 3, 4]


# https://pandas.pydata.org/docs/reference/api/pandas.DataFrame.from_dict.html
# the passed dict has to be of the form {field : array-like} or {field : dict},
# otherwise an error is raised
# https://github.com/pandas-dev/pandas/issues/12387
def test_create_patched_styler_for_dict_with_scalars_raises_error_as_expected():
    with pytest.raises(ValueError, match="If using all scalar values, you must pass an index"):
        StyledDataFrameViewerBridge.create_patched_styler({"a": 1})


def test_create_patched_styler_for_dict_keys_as_rows():
    ps = StyledDataFrameViewerBridge.create_patched_styler(
        df_dict,
        CreatePatchedStylerConfig(data_source_to_frame_hint="DictKeysAsRows"),
    )
    assert isinstance(ps, PatchedStyler)
    assert list(ps.internal_get_context().get_visible_frame().index) == list(df_dict.keys())


def test_create_patched_styler_fails_on_unsupported_data_source():
    assert StyledDataFrameViewerBridge.create_patched_styler([1]) == CreatePatchedStylerFailure(
        error_kind="UNSUPPORTED_DATA_SOURCE_TYPE",
        info="<class 'list'>",
    ).to_json()


def test_create_patched_styler_fails_on_invalid_fingerprint():
    assert StyledDataFrameViewerBridge.create_patched_styler(
        df,
        CreatePatchedStylerConfig(previous_fingerprint=""),
    ) == CreatePatchedStylerFailure(
        error_kind="INVALID_FINGERPRINT",
        info=create_fingerprint(df),
    ).to_json()


def test_create_patched_styler_fails_on_failing_eval_filter():
    assert StyledDataFrameViewerBridge.create_patched_styler(
        df,
        CreatePatchedStylerConfig(filter_eval_expr="xyz"),
    ) == CreatePatchedStylerFailure(
        error_kind="FILTER_FRAME_EVAL_FAILED",
        info="NameError(\"name 'xyz' is not defined\")",
    ).to_json()


def test_create_patched_styler_fails_on_wrong_filter_type():
    assert StyledDataFrameViewerBridge.create_patched_styler(
        df,
        CreatePatchedStylerConfig(filter_eval_expr="df.style"),
    ) == CreatePatchedStylerFailure(
        error_kind="FILTER_FRAME_OF_WRONG_TYPE",
        info="<class 'pandas.io.formats.style.Styler'>",
    ).to_json()
