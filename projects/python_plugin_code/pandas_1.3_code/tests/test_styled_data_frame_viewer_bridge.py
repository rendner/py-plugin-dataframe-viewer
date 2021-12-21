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

from plugin_code.patched_styler import PatchedStyler
from plugin_code.styled_data_frame_viewer_bridge import StyledDataFrameViewerBridge

df = pd.DataFrame()


def test_check_returns_true():
    assert StyledDataFrameViewerBridge.check()


def test_create_patched_styler_for_df():
    try:
        assert isinstance(StyledDataFrameViewerBridge.create_patched_styler(df), PatchedStyler)
    finally:
        StyledDataFrameViewerBridge.delete_all()


def test_create_patched_styler_for_styler():
    try:
        assert isinstance(StyledDataFrameViewerBridge.create_patched_styler(df.style), PatchedStyler)
    finally:
        StyledDataFrameViewerBridge.delete_all()


def test_created_patched_styler_are_added_to_internal_cache():
    refs = [
        StyledDataFrameViewerBridge.create_patched_styler(df),
        StyledDataFrameViewerBridge.create_patched_styler(df.style),
    ]
    try:
        assert len(StyledDataFrameViewerBridge.patched_styler_refs) == len(refs)
    finally:
        StyledDataFrameViewerBridge.delete_all()


def test_deleted_patched_styler_are_removed_from_internal_cache():
    refs = [
        StyledDataFrameViewerBridge.create_patched_styler(df),
        StyledDataFrameViewerBridge.create_patched_styler(df.style),
    ]
    [StyledDataFrameViewerBridge.delete_patched_styler(ref) for ref in refs]
    try:
        assert len(StyledDataFrameViewerBridge.patched_styler_refs) == 0
    finally:
        StyledDataFrameViewerBridge.delete_all()


def test_delete_all_clears_internal_cache():
    refs = [
        StyledDataFrameViewerBridge.create_patched_styler(df),
        StyledDataFrameViewerBridge.create_patched_styler(df.style),
    ]
    try:
        assert len(StyledDataFrameViewerBridge.patched_styler_refs) == len(refs)
        StyledDataFrameViewerBridge.delete_all()
        assert len(StyledDataFrameViewerBridge.patched_styler_refs) == 0
    finally:
        StyledDataFrameViewerBridge.delete_all()
