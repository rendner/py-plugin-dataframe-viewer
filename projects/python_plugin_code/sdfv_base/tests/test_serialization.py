from cms_rendner_sdfv.base.exceptions import TableSourceException, TableSourceErrorKind
from cms_rendner_sdfv.base.serialization import ExceptionJsonString, ResultJsonString
from cms_rendner_sdfv.base.types import Cell


def test_exception_json_string():
    actual = ExceptionJsonString.from_value(ValueError("Invalid"))
    expected = '{"info": "", "error_kind": "UNKNOWN_ERROR", "cause": "ValueError(\'Invalid\')"}'
    assert actual.value == expected
    assert isinstance(actual, ExceptionJsonString)


def test_table_source_exception_json_string():
    actual = ExceptionJsonString.from_value(TableSourceException(
        error_kind=TableSourceErrorKind.EVAL_FAILED,
    ))
    expected = '{"info": "", "error_kind": "EVAL_FAILED", "cause": "None"}'
    assert actual.value == expected
    assert isinstance(actual, ExceptionJsonString)

    actual = ExceptionJsonString.from_value(TableSourceException(
        error_kind=TableSourceErrorKind.EVAL_FAILED,
        info="Invalid"
    ))
    expected = '{"info": "Invalid", "error_kind": "EVAL_FAILED", "cause": "None"}'
    assert actual.value == expected

    actual = ExceptionJsonString.from_value(TableSourceException(
        error_kind=TableSourceErrorKind.EVAL_FAILED,
        info="Invalid",
        cause=ValueError("str expected"),
    ))
    expected = '{"info": "Invalid", "error_kind": "EVAL_FAILED", "cause": "ValueError(\'str expected\')"}'
    assert actual.value == expected

    try:
        raise TableSourceException(error_kind=TableSourceErrorKind.EVAL_FAILED) from ValueError("str expected")
    except Exception as ex:
        actual = ExceptionJsonString.from_value(ex)
        expected = '{"info": "", "error_kind": "EVAL_FAILED", "cause": "ValueError(\'str expected\')"}'
        assert actual.value == expected


def test_result_json_string():
    actual = ResultJsonString.from_value(1)
    expected = '1'
    assert actual.value == expected
    assert isinstance(actual, ResultJsonString)

    actual = ResultJsonString.from_value(Cell(value='123', meta='--=='))
    expected = '{"value": "123", "meta": "--=="}'
    assert actual.value == expected
    assert isinstance(actual, ResultJsonString)
