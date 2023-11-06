from itertools import chain

from pandas import DataFrame

from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.table_frame_generator import TableFrameGenerator
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria

df = DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_combined_chunks_do_not_include_filtered_out_values():
    filter_frame = df.filter(items=[1, 3, 4], axis='index')
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    expected = filter_frame.columns
    actual = ctx.get_frame_columns()
    assert list(actual) == list(expected)

    table = TableFrameGenerator(ctx).generate_by_combining_chunks(rows_per_chunk=2, cols_per_chunk=2)
    cell_values = list(map(lambda c: c.value, chain(*table.cells)))
    cell_values.sort()
    assert {0, 2, 5, 7, 10, 12, 15, 17, 20, 22}.intersection(set(cell_values)) == set()


# https://pandas.pydata.org/docs/getting_started/intro_tutorials/03_subset_data.html


def test_filter_by_columns():
    filter_frame = df[['col_0', 'col_4']]
    ctx = FrameContext(df, FilterCriteria(None, filter_frame.columns))

    expected_columns = filter_frame.columns
    actual_columns = ctx.get_frame_columns()
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [0, 4]
    actual_org_indices = ctx.get_org_indices_of_visible_columns(0, len(actual_columns))
    assert actual_org_indices == expected_org_indices


def test_filter_by_rows():
    filter_frame = df[df['col_0'] < 3]
    ctx = FrameContext(df, FilterCriteria(filter_frame.index, None))

    expected = filter_frame.index
    actual = ctx.get_frame_index()
    assert list(actual) == list(expected)


def test_filter_by_rows_and_columns():
    filter_frame = df.loc[df['col_0'] < 3, ['col_0', 'col_4']]
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    expected_index = filter_frame.index
    actual_index = ctx.get_frame_index()
    assert list(actual_index) == list(expected_index)

    expected_columns = filter_frame.columns
    actual_columns = ctx.get_frame_columns()
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [0, 4]
    actual_org_indices = ctx.get_org_indices_of_visible_columns(0, len(actual_columns))
    assert actual_org_indices == expected_org_indices


def test_filter_with_empty_rows():
    filter_frame = DataFrame()
    ctx = FrameContext(df, FilterCriteria(filter_frame.index, None))

    actual = ctx.get_frame_index()
    assert list(actual) == []


def test_filter_with_empty_columns():
    filter_frame = DataFrame()
    ctx = FrameContext(df, FilterCriteria(None, filter_frame.columns))

    actual_columns = ctx.get_frame_columns()
    assert list(actual_columns) == []

    actual_org_indices = ctx.get_org_indices_of_visible_columns(0, len(actual_columns))
    assert actual_org_indices == []


def test_filter_with_empty_rows_and_columns():
    filter_frame = DataFrame()
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual_index = ctx.get_frame_index()
    assert list(actual_index) == []

    actual_columns = ctx.get_frame_columns()
    assert list(actual_columns) == []


def test_filter_with_non_existing_rows_and_columns():
    filter_frame = DataFrame(index=[7, 8, 9], columns=["col_7", "col_8", "col_9"])
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual_index = ctx.get_frame_index()
    assert list(actual_index) == []

    actual_columns = ctx.get_frame_columns()
    assert list(actual_columns) == []

    actual_org_indices = ctx.get_org_indices_of_visible_columns(0, len(actual_columns))
    assert actual_org_indices == []


def test_filtered_frame_keeps_index_and_column_order():
    # The filter has the same cols and rows as df, but in reversed order.
    # Therefore, the filter doesn't filter out any row or col.
    filter_frame = df.copy().iloc[::-1, ::-1]
    ctx = FrameContext(df, FilterCriteria(None, filter_frame.columns))

    expected_rows = df.index
    actual_rows = ctx.get_frame_index()
    assert list(actual_rows) == list(expected_rows)

    expected_cols = df.columns
    actual_cols = ctx.get_frame_columns()
    assert list(actual_cols) == list(expected_cols)


def test_filter_with_df_filter():
    filter_frame = df.filter(items=['col_0', 'col_4'])
    ctx = FrameContext(df, FilterCriteria(None, filter_frame.columns))

    expected = filter_frame.columns
    actual = ctx.get_frame_columns()
    assert list(actual) == list(expected)
