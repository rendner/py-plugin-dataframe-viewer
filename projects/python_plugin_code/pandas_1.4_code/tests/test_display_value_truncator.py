import numpy as np

from plugin_code.display_value_truncator import DisplayValueTruncator


def test_truncation_for_cell_values():
    t = DisplayValueTruncator(6, True)
    assert t.truncate("abcdefgh") == "abcdef..."

    t = DisplayValueTruncator(12, True)
    assert t.truncate(["1", 2, 3, 4, 5]) == "['1', 2, 3, ...]"

    t = DisplayValueTruncator(8, True)
    assert t.truncate(np.array([1, 2, 3, 4, 5])) == "[1 2 3 4 ...]"

    t = DisplayValueTruncator(8, True)
    assert t.truncate(("1", 2, 3)) == "('1', 2, ...)"

    t = DisplayValueTruncator(14, True)
    assert t.truncate({"a": ["1", 2, 3]}) == "{'a': ['1', 2, ...]}"

    t = DisplayValueTruncator(10, True)
    assert t.truncate({(1, 2): [3, 4, 5]}) == "{(1, 2): [...]}"


def test_truncation_for_header_values():
    t = DisplayValueTruncator(6, False)
    assert t.truncate("abcdefgh") == "abcdef..."

    t = DisplayValueTruncator(10, False)
    assert t.truncate(["1", 2, 3, 4, 5]) == "[1, 2, 3, ...]"

    t = DisplayValueTruncator(8, False)
    assert t.truncate(np.array([1, 2, 3, 4, 5])) == "[1 2 3 4 ...]"

    t = DisplayValueTruncator(8, False)
    assert t.truncate(("1", 2, 3, 4)) == "(1, 2, 3, ...)"

    t = DisplayValueTruncator(12, False)
    assert t.truncate({"a": ["1", 2, 3, 4]}) == "{a: [1, 2, 3, ...]}"

    t = DisplayValueTruncator(12, False)
    assert t.truncate({(1, 2): ['3', 4, 5]}) == "{(1, 2): [3, ...]}"
