import pandas as pd

from cms_rendner_sdfv.base.types import TableFrame, TableFrameColumn, TableFrameCell
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from tests.helpers.asserts.assert_table_frames import assert_table_frames


def test_relabel_first_then_hide__axis_index():
    df = pd.DataFrame({"col": ["a", "b", "c"]})
    styler = df.style.relabel_index(["A", "B", "C"], axis="index").hide([1], axis="index")

    ctx = PatchedStylerContext(styler)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(1, 1)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['A'], ['C']],
            columns=[TableFrameColumn(dtype='string', labels=['col'])],
            cells=[[TableFrameCell(value='a')], [TableFrameCell(value='c')]],
        ),
    )


def test_hide_first_then_relabel__axis_index():
    df = pd.DataFrame({"col": ["a", "b", "c"]})
    styler = df.style.hide([1], axis="index").relabel_index(["A", "C"], axis="index")

    ctx = PatchedStylerContext(styler)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(1, 1)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['A'], ['C']],
            columns=[TableFrameColumn(dtype='string', labels=['col'])],
            cells=[[TableFrameCell(value='a')], [TableFrameCell(value='c')]],
        ),
    )


def test_relabel_first_then_hide__axis_columns():
    df = pd.DataFrame({
        "col_1": [1],
        "col_2": [2],
        "col_3": [3],
    })
    styler = df.style.relabel_index(["A", "B", "C"], axis="columns").hide(["col_2"], axis="columns")

    ctx = PatchedStylerContext(styler)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(1, 1)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0']],
            columns=[
                TableFrameColumn(dtype='int64', labels=['A']),
                TableFrameColumn(dtype='int64', labels=['C']),
            ],
            cells=[[TableFrameCell(value='1'), TableFrameCell(value='3')]],
        ),
    )


def test_hide_first_then_relabel__axis_columns():
    df = pd.DataFrame({
        "col_1": [1],
        "col_2": [2],
        "col_3": [3],
    })
    styler = df.style.hide(["col_2"], axis="columns").relabel_index(["A", "C"], axis="columns")

    ctx = PatchedStylerContext(styler)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(1, 1)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=[['0']],
            columns=[
                TableFrameColumn(dtype='int64', labels=['A']),
                TableFrameColumn(dtype='int64', labels=['C']),
            ],
            cells=[[TableFrameCell(value='1'), TableFrameCell(value='3')]],
        ),
    )
