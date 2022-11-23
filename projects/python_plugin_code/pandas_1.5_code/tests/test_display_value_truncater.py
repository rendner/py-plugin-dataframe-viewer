import numpy as np

from plugin_code.html_props_table_builder import DisplayValueTruncater


def test_truncation_for_cell_values():
    t = DisplayValueTruncater(6, True)
    assert t.truncate("abcdefgh") == "abcdef..."

    t = DisplayValueTruncater(12, True)
    assert t.truncate(["1", 2, 3, 4, 5]) == "['1', 2, 3, ...]"

    t = DisplayValueTruncater(8, True)
    assert t.truncate(np.array([1, 2, 3, 4, 5])) == "[1 2 3 4 ...]"

    t = DisplayValueTruncater(8, True)
    assert t.truncate(("1", 2, 3)) == "('1', 2, ...)"

    t = DisplayValueTruncater(14, True)
    assert t.truncate({"a": ["1", 2, 3]}) == "{'a': ['1', 2, ...]}"

    t = DisplayValueTruncater(10, True)
    assert t.truncate({(1, 2): [3, 4, 5]}) == "{(1, 2): [...]}"


def test_truncation_for_header_values():
    t = DisplayValueTruncater(6, False)
    assert t.truncate("abcdefgh") == "abcdef..."

    t = DisplayValueTruncater(10, False)
    assert t.truncate(["1", 2, 3, 4, 5]) == "[1, 2, 3, ...]"

    t = DisplayValueTruncater(8, False)
    assert t.truncate(np.array([1, 2, 3, 4, 5])) == "[1 2 3 4 ...]"

    t = DisplayValueTruncater(8, False)
    assert t.truncate(("1", 2, 3, 4)) == "(1, 2, 3, ...)"

    t = DisplayValueTruncater(12, False)
    assert t.truncate({"a": ["1", 2, 3, 4]}) == "{a: [1, 2, 3, ...]}"

    t = DisplayValueTruncater(12, False)
    assert t.truncate({(1, 2): ['3', 4, 5]}) == "{(1, 2): [3, ...]}"
