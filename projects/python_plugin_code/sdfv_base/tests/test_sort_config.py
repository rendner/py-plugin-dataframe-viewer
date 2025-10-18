import pytest

from cms_rendner_sdfv.base.types import SortConfig


def test_equals():
    a = SortConfig()
    b = SortConfig()
    assert a == b

    a = SortConfig()
    b = SortConfig([], [])
    assert a == b

    a = SortConfig([], [])
    b = SortConfig([], [])
    assert a == b

    a = SortConfig([0], [True])
    b = SortConfig([0], [True])
    assert a == b

    a = SortConfig([0], [True])
    b = SortConfig([1], [True])
    assert a != b

    a = SortConfig([0], [True])
    b = SortConfig([0], [False])
    assert a != b


def test_post_init():
    msg = "Size of 'column_idx' and 'ascending' does not match."
    with pytest.raises(AssertionError, match=msg):
        SortConfig([0], [])


def test_is_empty():
    assert SortConfig().is_empty()
    assert SortConfig([], []).is_empty()
    assert not SortConfig([0], [True]).is_empty()
