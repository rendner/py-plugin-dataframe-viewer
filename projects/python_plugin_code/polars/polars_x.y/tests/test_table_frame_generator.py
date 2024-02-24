import polars as pl
import pytest

from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell, TableFrameColumn
from cms_rendner_sdfv.polars.frame_context import FrameContext
from tests.helpers.asserts.assert_table_frames import assert_table_frames


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


@pytest.mark.parametrize("str_length, expected_value", [
    (1, '1…'),
    (2, '12…'),
    (3, '123…'),
    (4, '1234…'),
    (5, '12345'),
])
def test_config_string_cell_values_are_correct_truncated(str_length: int, expected_value: str):
    df = pl.DataFrame({"0": ["12345"]})

    with pl.Config() as cfg:
        cfg.set_fmt_str_lengths(str_length)
        actual = FrameContext(df).get_table_frame_generator().generate()

    assert actual.cells[0][0].value == expected_value


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
