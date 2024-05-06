import polars as pl

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell, TableFrameColumn
from cms_rendner_sdfv.polars.constants import CELL_MAX_LIST_LEN
from cms_rendner_sdfv.polars.frame_context import FrameContext
from tests.helpers.asserts.assert_table_frames import assert_table_frames


def test_cell_stringlike_values_have_no_double_quotes():
    data = [
        pl.Series("col1", ["a"], dtype=pl.String),
        pl.Series("col2", ["a"], dtype=pl.Enum(["a"])),
        pl.Series("col3", ["a"], dtype=pl.Categorical),
        pl.Series("col4", ["a"], dtype=pl.Utf8),
    ]
    df = pl.DataFrame(data)

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate()
    assert actual.cells == [
        [
            TableFrameCell(value='a'),
            TableFrameCell(value='a'),
            TableFrameCell(value='a'),
            TableFrameCell(value='a'),
        ],
    ]


def test_nested_cell_stringlike_values_have_double_quotes():
    data = [
        pl.Series("col1", [["a"]], dtype=pl.Array(pl.String, 1)),
    ]
    df = pl.DataFrame(data)

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate()
    assert actual.cells == [[TableFrameCell(value='["a"]')]]


def test_columns_with_different_row_value_types_returns_valid_cell_strings():
    df_with_string_and_numbers = pl.from_dict({"0": [1, "2"], "1": ["1", 2]})
    ctx = FrameContext(df_with_string_and_numbers)
    actual = ctx.get_table_frame_generator().generate()
    assert actual.cells == [
        [TableFrameCell(value='null'), TableFrameCell(value='1')],
        [TableFrameCell(value='2'), TableFrameCell(value='null')],
    ]


def test_generate_by_combining_chunks():
    df = pl.DataFrame(
        {
            "0": [0, 1, 2],
            "1": [3, 4, 5],
        }
    )
    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=None,
            columns=[
                TableFrameColumn(dtype='Int64', labels=['0']),
                TableFrameColumn(dtype='Int64', labels=['1']),
            ],
            cells=[
                [TableFrameCell(value='0'), TableFrameCell(value='3')],
                [TableFrameCell(value='1'), TableFrameCell(value='4')],
                [TableFrameCell(value='2'), TableFrameCell(value='5')],
            ],
        )
    )


def test_config_thousand_formatting():
    df = pl.DataFrame({"0": [1000]})

    with pl.Config() as cfg:
        cfg.set_thousands_separator("#")
        actual = FrameContext(df).get_table_frame_generator().generate()

    assert actual.cells[0][0].value == '1#000'


def test_config_float_precision():
    df = pl.DataFrame({"0": [1.23456789]})

    with pl.Config() as cfg:
        cfg.set_float_precision(3)
        actual = FrameContext(df).get_table_frame_generator().generate()

    assert actual.cells[0][0].value == '1.235'


def test_respects_plugin_max_str_length():
    df = pl.DataFrame({"0": "a" * 2 * CELL_MAX_STR_LEN})

    actual = FrameContext(df).get_table_frame_generator().generate()

    assert actual.cells[0][0].value.count('a') == CELL_MAX_STR_LEN
    # +1 for the truncation char
    assert len(actual.cells[0][0].value) == CELL_MAX_STR_LEN + 1


def test_respects_plugin_max_list_length():
    df = pl.DataFrame({"0": [["a"] * 2 * CELL_MAX_LIST_LEN]})

    actual = FrameContext(df).get_table_frame_generator().generate()

    assert actual.cells[0][0].value.count('a') == CELL_MAX_LIST_LEN


def test_describe():
    df = pl.DataFrame({
        'numeric': [1, 2, 3],
        'string': ['a', 'b', 'c'],
    })

    ctx = FrameContext(df)
    actual = ctx.get_table_frame_generator().generate_by_combining_chunks(2, 2)
    assert_table_frames(
        actual,
        TableFrame(
            index_labels=None,
            columns=[
                TableFrameColumn(
                    dtype='Int64',
                    labels=['numeric'],
                    describe={
                        'count': '3.0',
                        'null_count': '0.0',
                        'mean': '2.0',
                        'std': '1.0',
                        'min': '1.0',
                        '25%': '2.0',
                        '50%': '2.0',
                        '75%': '3.0',
                        'max': '3.0',
                    }
                ),
                TableFrameColumn(
                    dtype='String',
                    labels=['string'],
                    describe={
                        'count': '3',
                        'null_count': '0',
                        'min': 'a',
                        'max': 'c',
                    }
                ),
            ],
            cells=[
                [TableFrameCell(value='1'), TableFrameCell(value='a')],
                [TableFrameCell(value='2'), TableFrameCell(value='b')],
                [TableFrameCell(value='3'), TableFrameCell(value='c')],
            ],
        ),
        include_column_describe=True,
    )
