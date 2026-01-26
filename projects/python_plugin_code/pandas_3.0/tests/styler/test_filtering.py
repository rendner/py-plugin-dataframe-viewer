import pytest
from pandas import DataFrame, Index, IndexSlice

from cms_rendner_sdfv.base.types import CellMeta, FilterConfig, FilterResult, SortConfig, TableStructure, \
    TableStructureColumnInfo, TableStructureColumn, TextAlign
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from tests.helpers.asserts.assert_patched_styler_filtering import assert_patched_styler_filtering

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_filter_values_before_styling_breaks_styling():
    styler = df.style.highlight_min(subset=IndexSlice[2:4])
    assert "background-color: yellow;" in styler.to_html()

    # filter out the min value beforehand and style afterward will pick another min value (unwanted behavior)
    styler_filtered = df.filter(items=[1, 3, 4], axis='index').style.highlight_min(subset=IndexSlice[2:4])
    assert "background-color: yellow;" in styler_filtered.to_html()


def test_combined_chunks_do_not_include_a_highlighted_min_after_filtering_min_value_out():
    styler = df.style.highlight_min(subset=IndexSlice[2:4])
    assert "background-color: yellow;" in styler.to_html()

    ctx = PatchedStylerContext(styler)
    # filter out the min value
    ctx.apply_filter(FilterConfig(expr="df.filter(items=[1, 3, 4], axis='index')"))

    # expect: no styled min value
    chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(rows_per_chunk=2, cols_per_chunk=2)
    for row in chunk_data.cells:
        for entry in row:
            assert CellMeta.from_packed(entry.meta).background_color is None


def test_combined_chunks_do_include_highlighted_min_values_after_filtering():
    styler = df.style.highlight_min(subset=IndexSlice[2:4])
    assert "background-color: yellow;" in styler.to_html()

    ctx = PatchedStylerContext(styler)
    # filter, but include min values
    ctx.apply_filter(FilterConfig(expr="df.filter(items=[1, 2], axis='index')"))

    chunk_data = ctx.get_chunk_data_generator().generate_by_combining_chunks(rows_per_chunk=2, cols_per_chunk=2)

    highlighted_values_found = 0
    for row in chunk_data.cells:
        for entry in row:
            if entry.meta is not None:
                if CellMeta.from_packed(entry.meta).background_color == 'yellow':
                    highlighted_values_found += 1

    assert highlighted_values_found == len(df.columns)

# https://pandas.pydata.org/docs/getting_started/intro_tutorials/03_subset_data.html


def test_filter_by_columns():
    ctx = PatchedStylerContext(df.style)
    result = ctx.apply_filter(FilterConfig(expr="df[['col_0', 'col_4']]"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_by_rows():
    ctx = PatchedStylerContext(df.style)
    result = ctx.apply_filter(FilterConfig(expr="df[df['col_0'] < 3]"))

    expected = [0, 1, 2]
    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual) == list(expected)

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=3,
            columns_count=5,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_1'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_3'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_by_rows_and_columns():
    ctx = PatchedStylerContext(df.style)
    result = ctx.apply_filter(FilterConfig(expr="df.loc[df['col_0'] < 3, ['col_0', 'col_4']]"))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)

    assert list(actual.index) == [0, 1, 2]
    assert list(actual.columns) == ['col_0', 'col_4']

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=3,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_with_empty_df():
    ctx = PatchedStylerContext(df.style)
    result = ctx.apply_filter(FilterConfig(expr="DataFrame()"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=0,
            columns_count=0,
            column_info=TableStructureColumnInfo(
                columns=[],
                legend=None,
            ),
        ),
    )


def test_filter_with_non_existing_rows_and_columns():
    ctx = PatchedStylerContext(df.style)
    result = ctx.apply_filter(FilterConfig(expr="DataFrame(index=[7, 8, 9], columns=['col_7', 'col_8', 'col_9'])"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=0,
            columns_count=0,
            column_info=TableStructureColumnInfo(
                columns=[],
                legend=None,
            ),
        ),
    )


def test_filter_with_non_intersecting_hidden_columns():
    ctx = PatchedStylerContext(df.style.hide(axis="columns", subset=["col_2"]))
    result = ctx.apply_filter(FilterConfig(expr="df[['col_0', 'col_4']]"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_with_intersecting_hidden_columns():
    ctx = PatchedStylerContext(df.style.hide(axis="columns", subset=["col_4"]))
    result = ctx.apply_filter(FilterConfig(expr="df[['col_0', 'col_4']]"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=1,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_with_non_intersecting_hidden_rows():
    ctx = PatchedStylerContext(df.style.hide(axis="index", subset=[4]))
    result = ctx.apply_filter(FilterConfig(expr="df[df['col_0'] < 3]"))

    expected = [0, 1, 2]
    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual) == expected

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=3,
            columns_count=5,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_1'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_3'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_with_intersecting_hidden_rows():
    ctx = PatchedStylerContext(df.style.hide(axis="index", subset=[1]))
    result = ctx.apply_filter(FilterConfig(expr="df[df['col_0'] < 3]"))

    expected = [0, 2]
    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual) == expected

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=2,
            columns_count=5,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_1'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_3'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_with_df_filter():
    ctx = PatchedStylerContext(df.style)
    result = ctx.apply_filter(FilterConfig(expr="df.filter(items=['col_0', 'col_4'])"))

    assert result == FilterResult(
        sort=SortConfig(),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filtered_frame_keeps_index_and_column_order():
    ctx = PatchedStylerContext(df.style)
    # The filter has the same cols and rows as df, but in reversed order.
    # Therefore, the filter doesn't filter out any row or col.
    ctx.apply_filter(FilterConfig(expr="df.copy().iloc[::-1, ::-1]"))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == list(df.index)
    assert list(actual.columns) == list(df.columns)


@pytest.mark.parametrize("subset", [
    2,  # reduce to row
    "col_2",  # reduce to column
    (2, "col_2"),  # reduce to scalar
    IndexSlice[2:4],
    IndexSlice[2:4, ["col_2", "col_3"]],
    None,
])
def test_filtering_with_combined_chunks(subset):
    assert_patched_styler_filtering(
        df,
        lambda styler: styler.highlight_min(subset=subset),
        2,
        2,
        [1, 2, 4],
        'index',
    )


def test_filter_with_empty_str():
    ctx = PatchedStylerContext(df.style)
    ctx.apply_filter(FilterConfig(expr=""))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == list(df.index)
    assert list(actual.columns) == list(df.columns)


def test_filter_with_sorting_updates_sorting():
    ctx = PatchedStylerContext(df.style)
    ctx.apply_sort(
        SortConfig(
            [
                df.columns.get_loc("col_0"),
                df.columns.get_loc("col_2"),
            ],
            [True, True],
        )
    )
    result = ctx.apply_filter(FilterConfig(expr="df.filter(items=['col_0', 'col_4'])"))

    assert result == FilterResult(
        sort=SortConfig(column_idx=[0], ascending=[True]),
        table_structure=TableStructure(
            org_rows_count=5,
            org_columns_count=5,
            rows_count=5,
            columns_count=2,
            column_info=TableStructureColumnInfo(
                columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
                legend=None,
            ),
        ),
    )


def test_filter_fails_and_reverts_to_original_state():
    ctx = PatchedStylerContext(df.style)
    ctx.apply_sort(
        SortConfig(
            column_idx=[
                df.columns.get_loc("col_0"),
                df.columns.get_loc("col_2"),
            ],
            ascending=[True, True],
        )
    )

    # force an internal error
    ctx.apply_sort = None

    msg = "'NoneType' object is not callable"
    with pytest.raises(TypeError, match=msg):
        ctx.apply_filter(FilterConfig(expr="df.filter(items=['col_0', 'col_4'])"))

    # expect the unfiltered state
    assert ctx.get_table_structure() == TableStructure(
        org_rows_count=5,
        org_columns_count=5,
        rows_count=5,
        columns_count=5,
        column_info=TableStructureColumnInfo(
            columns=[
                    TableStructureColumn(dtype='int64', labels=['col_0'], id=0, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_1'], id=1, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_2'], id=2, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_3'], id=3, text_align=TextAlign.RIGHT),
                    TableStructureColumn(dtype='int64', labels=['col_4'], id=4, text_align=TextAlign.RIGHT),
                ],
            legend=None,
        ),
    )
