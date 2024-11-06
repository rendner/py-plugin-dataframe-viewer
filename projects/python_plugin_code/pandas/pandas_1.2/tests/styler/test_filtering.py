import pytest
from pandas import DataFrame, Index, IndexSlice

from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
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
    # note: extra space in front of color name (formatting bug in pandas 1.2)
    assert "background-color:  yellow;" in styler.render()

    # filter out the min value beforehand and style afterwards will pick another min value (unwanted behavior)
    styler_filtered = df.filter(items=[1, 3, 4], axis='index').style.highlight_min(subset=IndexSlice[2:4])
    # note: extra space in front of color name (formatting bug in pandas 1.2)
    assert "background-color:  yellow;" in styler_filtered.render()


def test_combined_chunks_do_not_include_a_highlighted_min_after_filtering_min_value_out():
    styler = df.style.highlight_min(subset=IndexSlice[2:4])
    # note: extra space in front of color name (formatting bug in pandas 1.2)
    assert "background-color:  yellow;" in styler.render()

    # filter out the min value
    filter_frame = df.filter(items=[1, 3, 4], axis='index')
    ctx = PatchedStylerContext(styler, FilterCriteria.from_frame(filter_frame))

    # expect: no styled min value
    table = ctx.get_table_frame_generator().generate_by_combining_chunks(rows_per_chunk=2, cols_per_chunk=2)
    for row in table.cells:
        for entry in row:
            assert entry.css is None


def test_combined_chunks_do_include_highlighted_min_values_after_filtering():
    styler = df.style.highlight_min(subset=IndexSlice[2:4])
    # note: extra space in front of color name (formatting bug in pandas 1.2)
    assert "background-color:  yellow;" in styler.render()

    # filter, but include min values
    filter_frame = df.filter(items=[1, 2], axis='index')
    ctx = PatchedStylerContext(styler, FilterCriteria.from_frame(filter_frame))

    table = ctx.get_table_frame_generator().generate_by_combining_chunks(rows_per_chunk=2, cols_per_chunk=2)

    highlighted_values_found = 0
    for row in table.cells:
        for entry in row:
            if entry.css is not None:
                # note: extra space in front of color name is removed by the "TableFrameGenerator"
                if entry.css['background-color'] == 'yellow':
                    highlighted_values_found += 1

    assert highlighted_values_found == len(df.columns)

# https://pandas.pydata.org/docs/getting_started/intro_tutorials/03_subset_data.html


def test_filter_by_columns():
    filter_frame = df[['col_0', 'col_4']]
    ctx = PatchedStylerContext(df.style, FilterCriteria(None, filter_frame.columns))

    expected_columns = filter_frame.columns
    actual_columns = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [0, 4]
    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == expected_org_indices


def test_filter_by_rows():
    filter_frame = df[df['col_0'] < 3]
    ctx = PatchedStylerContext(df.style, FilterCriteria(filter_frame.index, None))

    expected = filter_frame.index
    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual) == list(expected)


def test_filter_by_rows_and_columns():
    filter_frame = df.loc[df['col_0'] < 3, ['col_0', 'col_4']]
    ctx = PatchedStylerContext(df.style, FilterCriteria.from_frame(filter_frame))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)

    assert list(actual.index) == list(filter_frame.index)
    assert list(actual.columns) == list(filter_frame.columns)

    expected_org_indices = [0, 4]
    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == expected_org_indices


def test_filter_with_empty_rows():
    filter_frame = DataFrame()
    ctx = PatchedStylerContext(df.style, FilterCriteria(filter_frame.index, None))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).index
    assert list(actual) == []


def test_filter_with_empty_columns():
    filter_frame = DataFrame()
    ctx = PatchedStylerContext(df.style, FilterCriteria(None, filter_frame.columns))

    actual_columns = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual_columns) == []

    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == []


def test_filter_with_empty_rows_and_columns():
    filter_frame = DataFrame()
    ctx = PatchedStylerContext(df.style, FilterCriteria.from_frame(filter_frame))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == []
    assert list(actual.columns) == []


def test_filter_with_non_existing_rows_and_columns():
    filter_frame = DataFrame(index=[7, 8, 9], columns=["col_7", "col_8", "col_9"])
    ctx = PatchedStylerContext(df.style, FilterCriteria.from_frame(filter_frame))

    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region)
    assert list(actual.index) == []
    assert list(actual.columns) == []

    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == []


def test_filter_with_non_intersecting_hidden_columns():
    filter_frame = df[['col_0', 'col_4']]
    ctx = PatchedStylerContext(
        df.style.hide_columns(subset=["col_2"]),
        FilterCriteria(None, filter_frame.columns),
    )

    expected_columns = filter_frame.columns
    actual_columns = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [0, 4]
    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == expected_org_indices


def test_filter_with_intersecting_hidden_columns():
    filter_frame = df[['col_0', 'col_4']]
    ctx = PatchedStylerContext(
        df.style.hide_columns(subset=["col_4"]),
        FilterCriteria(None, filter_frame.columns),
    )

    expected_columns = Index(["col_0"])
    actual_columns = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual_columns) == list(expected_columns)

    expected_org_indices = [0]
    actual_org_indices = ctx.visible_frame.get_column_indices()
    assert actual_org_indices == expected_org_indices


def test_filter_with_df_filter():
    filter_frame = df.filter(items=['col_0', 'col_4'])
    ctx = PatchedStylerContext(df.style, FilterCriteria(None, filter_frame.columns))

    expected = filter_frame.columns
    actual = ctx.visible_frame.to_frame(ctx.visible_frame.region).columns
    assert list(actual) == list(expected)


def test_filtered_frame_keeps_index_and_column_order():
    # The filter has the same cols and rows as df, but in reversed order.
    # Therefore, the filter doesn't filter out any row or col.
    filter_frame = df.copy().iloc[::-1, ::-1]
    ctx = PatchedStylerContext(df.style, FilterCriteria.from_frame(filter_frame))

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
