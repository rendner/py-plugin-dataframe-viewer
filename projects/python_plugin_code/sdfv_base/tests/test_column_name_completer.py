from typing import Any, List

from cms_rendner_sdfv.base.table_source import AbstractColumnNameCompleter

DEFAULT_SOURCE = [0, 1, 10, "0", "1", "10", {'something': 'unexpected'}]


class TestColumnNameCompleter(AbstractColumnNameCompleter):
    def _resolve_column_names(self, source: Any, is_synthetic_df: bool) -> List[Any]:
        return source


def test_name_to_complete_is_zero():
    completer = TestColumnNameCompleter()

    assert completer.get_variants(DEFAULT_SOURCE, False, 0) == ['0']
    assert completer.get_variants(DEFAULT_SOURCE, False, "0") == ['"0"']


def test_name_to_complete_returns_all_matching_strings():
    completer = TestColumnNameCompleter()

    assert completer.get_variants(DEFAULT_SOURCE, False, "0") == ['"0"']
    assert completer.get_variants(DEFAULT_SOURCE, False, "1") == ['"1"', '"10"']
    assert completer.get_variants(DEFAULT_SOURCE, False, "10") == ['"10"']
    assert completer.get_variants(DEFAULT_SOURCE, False, "100") == []


def test_name_to_complete_returns_all_matching_integers():
    completer = TestColumnNameCompleter()

    assert completer.get_variants(DEFAULT_SOURCE, False, 0) == ['0']
    assert completer.get_variants(DEFAULT_SOURCE, False, 1) == ['1', '10']
    assert completer.get_variants(DEFAULT_SOURCE, False, 10) == ['10']
    assert completer.get_variants(DEFAULT_SOURCE, False, 100) == []


def test_name_to_complete_is_empty_str_returns_all_str_variants():
    completer = TestColumnNameCompleter()

    assert completer.get_variants(DEFAULT_SOURCE, False, "") == ['"0"', '"1"', '"10"']


def test_name_to_complete_is_none_returns_all_variants():
    completer = TestColumnNameCompleter()

    assert completer.get_variants(DEFAULT_SOURCE, False, None) == ['0', '1', '10', '"0"', '"1"', '"10"']


def test_strings_with_leading_quotes():
    completer = TestColumnNameCompleter()

    assert completer.get_variants([
        '"A"',
        '""B""',
        '"""C"""',
        "'D'",
        "''E''",
        "'''F'''",
    ], False, None) == [
        '""A""',
        '"""B"""',
        '""""C""""',
        '"\'D\'"',
        '"\'\'E\'\'"',
        '"\'\'\'F\'\'\'"',
    ]

