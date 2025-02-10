import polars as pl

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.types import ChunkData, Cell
from cms_rendner_sdfv.polars.constants import CELL_MAX_LIST_LEN
from cms_rendner_sdfv.polars.frame_context import FrameContext


def test_cell_stringlike_values_have_no_double_quotes():
    data = [
        pl.Series("col1", ["a"], dtype=pl.String),
        pl.Series("col2", ["a"], dtype=pl.Enum(["a"])),
        pl.Series("col3", ["a"], dtype=pl.Categorical),
        pl.Series("col4", ["a"], dtype=pl.Utf8),
    ]
    df = pl.DataFrame(data)

    ctx = FrameContext(df)
    actual = ctx.get_chunk_data_generator().generate()
    assert actual.cells == [
        [
            Cell(value='a'),
            Cell(value='a'),
            Cell(value='a'),
            Cell(value='a'),
        ],
    ]


def test_nested_cell_stringlike_values_have_double_quotes():
    data = [
        pl.Series("col1", [["a"]], dtype=pl.Array(pl.String, 1)),
    ]
    df = pl.DataFrame(data)

    ctx = FrameContext(df)
    actual = ctx.get_chunk_data_generator().generate()
    assert actual.cells == [[Cell(value='["a"]')]]


def test_columns_with_different_row_value_types_returns_valid_cell_strings():
    df_with_string_and_numbers = pl.from_dict({"0": [1, "2"], "1": ["1", 2]})
    ctx = FrameContext(df_with_string_and_numbers)
    actual = ctx.get_chunk_data_generator().generate()
    assert actual.cells == [
        [Cell(value='null'), Cell(value='1')],
        [Cell(value='2'), Cell(value='null')],
    ]


def test_generate_by_combining_chunks():
    df = pl.DataFrame(
        {
            "0": [0, 1, 2],
            "1": [3, 4, 5],
        }
    )
    ctx = FrameContext(df)
    actual = ctx.get_chunk_data_generator().generate_by_combining_chunks(2, 2)
    assert actual == ChunkData(
        index_labels=None,
        cells=[
            [Cell(value='0'), Cell(value='3')],
            [Cell(value='1'), Cell(value='4')],
            [Cell(value='2'), Cell(value='5')],
        ],
    )


def test_config_thousand_formatting():
    df = pl.DataFrame({"0": [1000]})

    with pl.Config() as cfg:
        cfg.set_thousands_separator("#")
        actual = FrameContext(df).get_chunk_data_generator().generate()

    assert actual.cells[0][0].value == '1#000'


def test_config_float_precision():
    df = pl.DataFrame({"0": [1.23456789]})

    with pl.Config() as cfg:
        cfg.set_float_precision(3)
        actual = FrameContext(df).get_chunk_data_generator().generate()

    assert actual.cells[0][0].value == '1.235'


def test_respects_plugin_max_str_length():
    df = pl.DataFrame({"0": "a" * 2 * CELL_MAX_STR_LEN})

    actual = FrameContext(df).get_chunk_data_generator().generate()

    assert actual.cells[0][0].value.count('a') == CELL_MAX_STR_LEN
    # +1 for the truncation char
    assert len(actual.cells[0][0].value) == CELL_MAX_STR_LEN + 1


def test_respects_plugin_max_list_length():
    df = pl.DataFrame({"0": [["a"] * 2 * CELL_MAX_LIST_LEN]})

    actual = FrameContext(df).get_chunk_data_generator().generate()

    assert actual.cells[0][0].value.count('a') == CELL_MAX_LIST_LEN
