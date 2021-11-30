import pandas as pd
import numpy as np
import pytest

from plugin_code.apply_fallback_patch import ApplyFallbackPatch
from plugin_code.apply_map_fallback_patch import ApplyMapFallbackPatch
from plugin_code.background_gradient_patch import BackgroundGradientPatch
from plugin_code.highlight_between_patch import HighlightBetweenPatch

from plugin_code.highlight_extrema_patch import HighlightExtremaPatch
from plugin_code.patched_styler import PatchedStyler

df = pd.DataFrame.from_dict({"col_0": [0, 1, 2, 3, np.nan]})


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
