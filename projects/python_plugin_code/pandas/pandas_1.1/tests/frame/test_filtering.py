from pandas import DataFrame

from cms_rendner_sdfv.base.types import TableFrame, TableFrameColumn, TableFrameCell
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from tests.helpers.asserts.assert_table_frames import assert_table_frames

df = DataFrame.from_dict({
    "col_0": [0,   1,   2, 3,  4],
    "col_1": [5,   6,  7,  8,  9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def test_combined_chunks_do_not_include_filtered_out_values():
    filter_frame = df.filter(items=[1, 3, 4], axis='index')
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual_frame = ctx.get_table_frame_generator().generate_by_combining_chunks(rows_per_chunk=2, cols_per_chunk=2)
    assert_table_frames(
        actual_frame,
        TableFrame(
            columns=[
                TableFrameColumn(dtype='int64', labels=['col_0']),
                TableFrameColumn(dtype='int64', labels=['col_1']),
                TableFrameColumn(dtype='int64', labels=['col_2']),
                TableFrameColumn(dtype='int64', labels=['col_3']),
                TableFrameColumn(dtype='int64', labels=['col_4'])
            ],
            index_labels=[['1'], ['3'], ['4']],
            cells=[
                [
                    TableFrameCell(value='1'),
                    TableFrameCell(value='6'),
                    TableFrameCell(value='11'),
                    TableFrameCell(value='16'),
                    TableFrameCell(value='21'),
                ],
                [
                    TableFrameCell(value='3'),
                    TableFrameCell(value='8'),
                    TableFrameCell(value='13'),
                    TableFrameCell(value='18'),
                    TableFrameCell(value='23'),
                ],
                [
                    TableFrameCell(value='4'),
                    TableFrameCell(value='9'),
                    TableFrameCell(value='14'),
                    TableFrameCell(value='19'),
                    TableFrameCell(value='24'),
                ]
            ],
        )
    )


# https://pandas.pydata.org/docs/getting_started/intro_tutorials/03_subset_data.html


def test_filter_by_columns():
    filter_frame = df[['col_0', 'col_4']]
    ctx = FrameContext(df, FilterCriteria(None, filter_frame.columns))

    expected_columns = filter_frame.columns
    actual_columns = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [0, 4]
    actual_org_indices = ctx.visible_frame.get_column_indices(0, len(actual_columns))
    assert actual_org_indices == expected_org_indices


def test_filter_by_rows():
    filter_frame = df[df['col_0'] < 3]
    ctx = FrameContext(df, FilterCriteria(filter_frame.index, None))

    expected = filter_frame.index
    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual) == list(expected)


def test_filter_by_rows_and_columns():
    filter_frame = df.loc[df['col_0'] < 3, ['col_0', 'col_4']]
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == list(filter_frame.index)
    assert list(actual.columns) == list(filter_frame.columns)

    expected_org_indices = [0, 4]
    actual_org_indices = ctx.visible_frame.get_column_indices(0, len(actual.columns))
    assert actual_org_indices == expected_org_indices


def test_filter_with_empty_rows():
    filter_frame = DataFrame()
    ctx = FrameContext(df, FilterCriteria(filter_frame.index, None))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual) == []


def test_filter_with_empty_columns():
    filter_frame = DataFrame()
    ctx = FrameContext(df, FilterCriteria(None, filter_frame.columns))

    actual_columns = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual_columns) == []

    actual_org_indices = ctx.visible_frame.get_column_indices(0, len(actual_columns))
    assert actual_org_indices == []


def test_filter_with_empty_rows_and_columns():
    filter_frame = DataFrame()
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == []
    assert list(actual.columns) == []


def test_filter_with_non_existing_rows_and_columns():
    filter_frame = DataFrame(index=[7, 8, 9], columns=["col_7", "col_8", "col_9"])
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == []
    assert list(actual.columns) == []

    actual_org_indices = ctx.visible_frame.get_column_indices(0, len(actual.columns))
    assert actual_org_indices == []


def test_filtered_frame_keeps_index_and_column_order():
    # The filter has the same cols and rows as df, but in reversed order.
    # Therefore, the filter doesn't filter out any row or col.
    filter_frame = df.copy().iloc[::-1, ::-1]
    ctx = FrameContext(df, FilterCriteria.from_frame(filter_frame))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == list(df.index)
    assert list(actual.columns) == list(df.columns)


def test_filter_with_df_filter():
    filter_frame = df.filter(items=['col_0', 'col_4'])
    ctx = FrameContext(df, FilterCriteria(None, filter_frame.columns))

    expected = filter_frame.columns
    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual) == list(expected)
