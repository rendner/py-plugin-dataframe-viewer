import pandas as pd

from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


def test_relabel_first_then_hide__axis_index():
    df = pd.DataFrame({"col": ["a", "b", "c"]})
    styler = df.style.relabel_index(["A", "B", "C"], axis="index").hide([1], axis="index")

    ctx = PatchedStylerContext(styler)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(1, 1)
    assert actual == TableFrame(
        index_labels=[['A'], ['C']],
        cells=[[TableFrameCell(value='a')], [TableFrameCell(value='c')]],
    )


def test_hide_first_then_relabel__axis_index():
    df = pd.DataFrame({"col": ["a", "b", "c"]})
    styler = df.style.hide([1], axis="index").relabel_index(["A", "C"], axis="index")

    ctx = PatchedStylerContext(styler)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(1, 1)
    assert actual == TableFrame(
        index_labels=[['A'], ['C']],
        cells=[[TableFrameCell(value='a')], [TableFrameCell(value='c')]],
    )
