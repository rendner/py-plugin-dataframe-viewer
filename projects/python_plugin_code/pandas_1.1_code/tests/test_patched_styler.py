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

from plugin_code.apply_map_fallback_patch import ApplyMapFallbackPatch
from plugin_code.background_gradient_patch import BackgroundGradientPatch
from plugin_code.highlight_extrema_patch import HighlightExtremaPatch
from plugin_code.patched_styler import PatchedStyler

df = pd.DataFrame.from_dict({"col_0": [0, 1, 2, 3, np.nan]})


def test_should_handle_highlight_min():
    patched_styler = PatchedStyler(df.style.highlight_min())
    assert len(patched_styler._PatchedStyler__patched_styles) == 1
    assert isinstance(patched_styler._PatchedStyler__patched_styles[0], HighlightExtremaPatch)


def test_should_handle_highlight_max():
    patched_styler = PatchedStyler(df.style.highlight_max())
    assert len(patched_styler._PatchedStyler__patched_styles) == 1
    assert isinstance(patched_styler._PatchedStyler__patched_styles[0], HighlightExtremaPatch)


def test_should_handle_background_gradient():
    patched_styler = PatchedStyler(df.style.background_gradient())
    assert len(patched_styler._PatchedStyler__patched_styles) == 1
    assert isinstance(patched_styler._PatchedStyler__patched_styles[0], BackgroundGradientPatch)


def test_should_handle_highlight_null():
    patched_styler = PatchedStyler(df.style.highlight_null())
    assert len(patched_styler._PatchedStyler__patched_styles) == 1
    assert isinstance(patched_styler._PatchedStyler__patched_styles[0], ApplyMapFallbackPatch)


def test_should_handle_set_properties():
    patched_styler = PatchedStyler(df.style.set_properties())
    assert len(patched_styler._PatchedStyler__patched_styles) == 1
    assert isinstance(patched_styler._PatchedStyler__patched_styles[0], ApplyMapFallbackPatch)
