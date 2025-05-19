import pandas as pd
import numpy as np
import pytest

from cms_rendner_sdfv.base.types import ChunkDataResponse, Cell, TableStructureColumn, \
    TableStructureColumnInfo, TableStructureLegend, TableInfo, TableStructure, TableSourceKind, Region, CellMeta, \
    TextAlign
from cms_rendner_sdfv.pandas.styler.patched_styler import PatchedStyler
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext, FilterCriteria
from cms_rendner_sdfv.pandas.styler.style_functions_validator import StyleFunctionsValidator
from cms_rendner_sdfv.pandas.styler.types import ValidatedChunkData, StyleFunctionValidationProblem, StyleFunctionInfo
from tests.helpers.asserts.assert_patched_styler import assert_patched_styler

np.random.seed(123456)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_compute_chunk_data():
    ps = PatchedStyler(PatchedStylerContext(df.style), "finger-1")
    actual = ps.compute_chunk_data(Region(0, 0, 2, 2))

    assert actual == ps.serialize(ChunkDataResponse(
        row_headers=[['0'], ['1']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='5', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='1', meta=CellMeta(cmap_value=25000).pack()),
                Cell(value='6', meta=CellMeta(cmap_value=25000).pack()),
            ],
        ],
    ))


def test_compute_chunk_data_with_multiindex():
    midx = pd.MultiIndex.from_product([[("A", "B"), "y"], ["a", "b", "c"]])
    my_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx, columns=midx)
    ps = PatchedStyler(PatchedStylerContext(my_df.style), "finger-1")
    actual = ps.compute_chunk_data(Region(0, 0, 2, 2))

    assert actual == ps.serialize(ChunkDataResponse(
        row_headers=[['(A, B)', 'a'], ['(A, B)', 'b']],
        cells=[
            [
                Cell(value='0', meta=CellMeta.min().pack()),
                Cell(value='1', meta=CellMeta.min().pack()),
            ],
            [
                Cell(value='6', meta=CellMeta(cmap_value=20000).pack()),
                Cell(value='7', meta=CellMeta(cmap_value=20000).pack()),
            ],
        ],
    ))


def test_validate_and_compute_chunk_data():
    ps = PatchedStyler(PatchedStylerContext(df.style), "finger-1")
    actual = ps.validate_and_compute_chunk_data(Region(0, 0, 2, 2))

    assert actual == ps.serialize(
        ValidatedChunkData(
            data=ChunkDataResponse(
                row_headers=[["0"], ["1"]],
                cells=[
                    [
                        Cell(value="0", meta=CellMeta.min().pack()),
                        Cell(value="5", meta=CellMeta.min().pack()),
                    ],
                    [
                        Cell(value="1", meta=CellMeta(cmap_value=25000).pack()),
                        Cell(value="6", meta=CellMeta(cmap_value=25000).pack()),
                    ],
                ],
            ),
            problems=None,
        )
    )


def test_table_info_with_different_column_types():
    my_df = pd.DataFrame.from_dict({
        'a': [1],
        'b': [1.0],
        'c': [1j],
        'd': ['1'],
        'e': [True],
        'f': [np.datetime64('2025-12-01')],
    })
    ps = PatchedStyler(PatchedStylerContext(my_df.style), "finger-1")

    assert ps.get_info() == ps.serialize(
        TableInfo(
            kind=TableSourceKind.PATCHED_STYLER.name,
            structure=TableStructure(
                org_rows_count=len(my_df.index),
                org_columns_count=len(my_df.columns),
                rows_count=len(my_df.index),
                columns_count=len(my_df.columns),
                fingerprint="finger-1",
                column_info=TableStructureColumnInfo(
                    legend=None,
                    columns=[
                        TableStructureColumn(dtype='int64', labels=['a'], id=0, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype='float64', labels=['b'], id=1, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype='complex128', labels=['c'], id=2, text_align=TextAlign.RIGHT),
                        TableStructureColumn(dtype='object', labels=['d'], id=3),
                        TableStructureColumn(dtype='bool', labels=['e'], id=4),
                        TableStructureColumn(dtype='datetime64[ns]', labels=['f'], id=5),
                    ],
                )
            ),
        )
    )


def test_table_info_with_none_leveled_column_names():
    d = {
        ("A", "B"): [1, 2, 3],
        "B": [1, 2, 3],
        "A": [1, 2, 3],
        101: [4, 5, 6],
        0: [9, 9, 9],
    }
    my_df = pd.DataFrame.from_dict(d)
    ps = PatchedStyler(PatchedStylerContext(my_df.style), "finger-1")
    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(my_df.index),
            org_columns_count=len(my_df.columns),
            rows_count=len(my_df.index),
            columns_count=len(my_df.columns),
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(dtype='int64', labels=['(A, B)'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['B'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['A'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['101'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['0'], id=4, text_align=TextAlign.RIGHT),
                ],
            )
        ),
    ))


def test_table_info_with_leveled_column_names():
    midx = pd.MultiIndex.from_product([[("A", "B"), "y"], ["a", "b", "c"]])
    my_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx, columns=midx)
    ps = PatchedStyler(PatchedStylerContext(my_df.style), "finger-1")
    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(my_df.index),
            org_columns_count=len(my_df.columns),
            rows_count=len(my_df.index),
            columns_count=len(my_df.columns),
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(dtype='int64', labels=['(A, B)', 'a'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['(A, B)', 'b'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['(A, B)', 'c'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['y', 'a'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['y', 'b'], id=4, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['y', 'c'], id=5, text_align=TextAlign.RIGHT),
                ],
            )
        ),
    ))


def test_table_info_with_str_and_int_column_names():
    d = {"B": [1], "A": [1], 101: [1], 0: [1]}
    styler = pd.DataFrame.from_dict(d).style
    ps = PatchedStyler(PatchedStylerContext(styler), "finger-1")

    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(styler.data.index),
            org_columns_count=len(styler.data.columns),
            rows_count=len(styler.data.index),
            columns_count=len(styler.data.columns),
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(dtype='int64', labels=['B'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['A'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['101'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['0'], id=3, text_align=TextAlign.RIGHT)
                ],
            )
        ),
    ))


def test_table_info_after_hiding_all_columns():
    styler = multi_df.style.hide(axis="columns", subset=multi_df.columns)
    ps = PatchedStyler(PatchedStylerContext(styler), "finger-1")

    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(styler.data.index),
            org_columns_count=len(styler.data.columns),
            rows_count=0,
            columns_count=0,
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(columns=[], legend=None)
        ),
    ))


def test_table_info_after_hiding_all_rows():
    styler = multi_df.style.hide(axis="index", subset=multi_df.index)
    ps = PatchedStyler(PatchedStylerContext(styler), "finger-1")

    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(styler.data.index),
            org_columns_count=len(styler.data.columns),
            rows_count=0,
            columns_count=0,
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(columns=[], legend=None)
        ),
    ))


def test_table_info_after_hiding_some_rows_and_cols():
    styler = df.style \
        .hide(axis="index", subset=pd.IndexSlice[1:3]) \
        .hide(axis="columns", subset=["col_1", "col_3"])
    ps = PatchedStyler(PatchedStylerContext(styler), "finger-1")

    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(styler.data.index),
            org_columns_count=len(styler.data.columns),
            rows_count=2,
            columns_count=3,
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
            )
        ),
    ))


def test_table_info_after_hiding_some_rows_and_cols_and_filtering():
    styler = df.style \
        .hide(axis="index", subset=pd.IndexSlice[1:3]) \
        .hide(axis="columns", subset=["col_1", "col_3"])
    filter_frame = pd.DataFrame(index=df.index[1:], columns=df.columns[1:])
    ps = PatchedStyler(
        PatchedStylerContext(styler, FilterCriteria.from_frame(filter_frame)),
        "finger-1",
    )

    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(styler.data.index),
            org_columns_count=len(styler.data.columns),
            rows_count=1,
            columns_count=2,
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=None,
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
            )
        ),
    ))


def test_table_info_respects_configured_col_label_formatting():
    s = multi_df.style.format_index(str.upper, axis='columns')
    ps = PatchedStyler(PatchedStylerContext(s), "finger-1")

    assert ps.get_info() == ps.serialize(TableInfo(
        kind=TableSourceKind.PATCHED_STYLER.name,
        structure=TableStructure(
            org_rows_count=len(multi_df.index),
            org_columns_count=len(multi_df.columns),
            rows_count=len(multi_df.index),
            columns_count=len(multi_df.columns),
            fingerprint="finger-1",
            column_info=TableStructureColumnInfo(
                legend=TableStructureLegend(index=['rows-char', 'rows-color'], column=['cols-char', 'cols-color']),
                columns=[
                    TableStructureColumn(dtype='int64', labels=['X', 'A'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['X', 'B'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['X', 'C'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['Y', 'A'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['Y', 'B'], id=4, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['Y', 'C'], id=5, text_align=TextAlign.RIGHT)
                ],
            )
        ),
    ))


class FakeFormatterDict(dict):
    def get(self, key):
        return self.formatter

    @staticmethod
    def formatter(x):
        return x


@pytest.mark.parametrize("formatter", [FakeFormatterDict(), lambda x: x])
@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_render_chunk_translates_display_funcs_correct_also_with_hidden_rows_cols(
        formatter,
        rows_per_chunk,
        cols_per_chunk,
):
    assert_patched_styler(
        df,
        lambda styler: styler
        .hide(axis="index", subset=pd.IndexSlice[1:3])
        .hide(axis="columns", subset=["col_1", "col_3"])
        .format(formatter=formatter),
        rows_per_chunk,
        cols_per_chunk
    )


def test_should_not_revalidate_faulty_styling_functions():
    def is_in_validator():
        import inspect
        frame = inspect.currentframe().f_back
        while frame:
            instance = frame.f_locals.get('self', None)
            if instance is not None and instance.__class__.__name__ == StyleFunctionsValidator.__name__:
                return True
            frame = frame.f_back
        return False

    raise_in_validator = False

    def my_style_func(s):
        nonlocal raise_in_validator
        if raise_in_validator and is_in_validator():
            raise Exception("panic")
        return ['' for _ in s]

    styler = df.style.apply(my_style_func, axis='index')
    ctx = PatchedStylerContext(styler)
    ps = PatchedStyler(ctx, "")

    result = ps.validate_and_compute_chunk_data(Region(0, 0, 2, 2))
    assert result == ps.serialize(
        ValidatedChunkData(
            data=ChunkDataResponse(
                row_headers=[["0"], ["1"]],
                cells=[
                    [
                        Cell(value="0", meta=CellMeta.min().pack()),
                        Cell(value="5", meta=CellMeta.min().pack()),
                    ],
                    [
                        Cell(value="1", meta=CellMeta(cmap_value=25000).pack()),
                        Cell(value="6", meta=CellMeta(cmap_value=25000).pack()),
                    ],
                ],
            ),
            problems=None,
        )
    )

    raise_in_validator = True

    result = ps.validate_and_compute_chunk_data(Region(0, 0, 2, 2))
    assert result == ps.serialize(
        ValidatedChunkData(
            data=ChunkDataResponse(
                row_headers=[["0"], ["1"]],
                cells=[
                    [
                        Cell(value="0", meta=CellMeta.min().pack()),
                        Cell(value="5", meta=CellMeta.min().pack()),
                    ],
                    [
                        Cell(value="1", meta=CellMeta(cmap_value=25000).pack()),
                        Cell(value="6", meta=CellMeta(cmap_value=25000).pack()),
                    ],
                ],
            ),
            problems=[
                StyleFunctionValidationProblem(
                    reason="EXCEPTION",
                    message="panic",
                    func_info=StyleFunctionInfo(
                        index=0,
                        qname="test_should_not_revalidate_faulty_styling_functions.<locals>.my_style_func",
                        resolved_name="my_style_func",
                        axis="index",
                        is_chunk_parent_requested=False,
                        is_apply=True,
                        is_pandas_builtin=False,
                        is_supported=False,
                    )
                ),
            ],
        )
    )

    result = ps.validate_and_compute_chunk_data(Region(0, 0, 2, 2))
    assert result == ps.serialize(
        ValidatedChunkData(
            data=ChunkDataResponse(
                row_headers=[["0"], ["1"]],
                cells=[
                    [
                        Cell(value="0", meta=CellMeta.min().pack()),
                        Cell(value="5", meta=CellMeta.min().pack()),
                    ],
                    [
                        Cell(value="1", meta=CellMeta(cmap_value=25000).pack()),
                        Cell(value="6", meta=CellMeta(cmap_value=25000).pack()),
                    ],
                ],
            ),
            problems=None,
        )
    )
