import pandas as pd
import pytest
from pandas import DataFrame, option_context

from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from tests.helpers.chunk_data_validator import ChunkDataValidator

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


def _assert_frame_formatting(df: DataFrame, rows_per_chunk: int, cols_per_chunk: int):
    result = ChunkDataValidator.with_context(FrameContext(df)).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected


@pytest.mark.parametrize(
    "rows_per_chunk, cols_per_chunk", [
        (1, 2),
        df.shape  # single chunk
    ])
def test_chunked(rows_per_chunk, cols_per_chunk):
    _assert_frame_formatting(df, rows_per_chunk, cols_per_chunk)


def test_use_option__display_precision():
    df_with_floats = DataFrame(data=[1.0123456789, [1.0123456789], {'a': 1.0123456789}, 123456789])

    with option_context('display.precision', 2):
        chunk_data = FrameContext(df_with_floats).get_chunk_data_generator().generate()

    assert chunk_data.cells[0][0].value == "1.01"
    # precision isn't applied to nested data
    assert chunk_data.cells[1][0].value == "[1.0123456789]"
    assert chunk_data.cells[2][0].value == "{'a': 1.0123456789}"
    # precision isn't applied to nested integers
    assert chunk_data.cells[3][0].value == "123456789"


def test_use_option__display_float_format():
    df_with_floats = DataFrame(data=[1.0123456789, [1.0123456789], {'a': 1.0123456789}, 123456789])

    with option_context('display.float_format', '${:,.2f}'.format):
        chunk_data = FrameContext(df_with_floats).get_chunk_data_generator().generate()

    assert chunk_data.cells[0][0].value == "$1.01"
    # float_format isn't applied to nested data
    assert chunk_data.cells[1][0].value == "[1.0123456789]"
    assert chunk_data.cells[2][0].value == "{'a': 1.0123456789}"
    # float_format isn't applied to nested integers
    assert chunk_data.cells[3][0].value == "123456789"


def test_use_option__display_float_format_is_preferred_over_display_precision():
    df_with_floats = DataFrame(data=[1.0123456789])

    with option_context(
            'display.float_format', '${:,.2f}'.format,
            'display.precision', 4,
    ):
        chunk_data = FrameContext(df_with_floats).get_chunk_data_generator().generate()

    assert chunk_data.cells[0][0].value == "$1.01"


def test_use_option__max_seq_items():
    df_with_seq = DataFrame(data=[[range(5)]])

    with option_context('display.max_seq_items', 2):
        chunk_data = FrameContext(df_with_seq).get_chunk_data_generator().generate()

    assert chunk_data.cells[0][0].value == "(0, 1, ...)"
