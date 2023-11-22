import pytest

from cms_rendner_sdfv.base.types import Region


def test_is_empty():
    assert Region().is_empty()
    assert Region(rows=1).is_empty()
    assert Region(cols=1).is_empty()
    assert Region(first_row=1).is_empty()
    assert Region(first_col=1).is_empty()
    assert Region(first_row=1, first_col=1).is_empty()

    assert not Region(rows=1, cols=1).is_empty()
    assert not Region(first_row=2, first_col=3, rows=1, cols=1).is_empty()


def test_is_valid():
    assert Region().is_valid()

    assert not Region(first_row=-1).is_valid()
    assert not Region(first_col=-1).is_valid()
    assert not Region(rows=-1).is_valid()
    assert not Region(cols=-1).is_valid()


def test_iterate_chunkwise():
    chunks = [chunk for chunk in Region().iterate_chunkwise(2, 2)]
    assert chunks == []

    chunks = [chunk for chunk in Region(rows=1).iterate_chunkwise(2, 2)]
    assert chunks == []

    chunks = [chunk for chunk in Region(cols=1).iterate_chunkwise(2, 2)]
    assert chunks == []

    chunks = [chunk for chunk in Region(rows=1, cols=1).iterate_chunkwise(2, 2)]
    assert chunks == [Region(rows=1, cols=1)]

    chunks = [chunk for chunk in Region(first_row=1, first_col=1, rows=3, cols=1).iterate_chunkwise(2, 2)]
    assert chunks == [Region(rows=2, cols=1), Region(first_row=2, rows=1, cols=1)]

    with pytest.raises(ValueError):
        # noinspection PyStatementEffect
        [chunk for chunk in Region(rows=-1).iterate_chunkwise(2, 2)]

    with pytest.raises(ValueError):
        # noinspection PyStatementEffect
        [chunk for chunk in Region(rows=2, cols=2).iterate_chunkwise(-2, 2)]


def test_get_bounded_region():
    a = Region()
    b = Region()
    assert a.get_bounded_region(b) == Region()

    a = Region(first_row=1, first_col=1, rows=4, cols=4)
    b = Region(first_row=1, first_col=1, rows=4, cols=4)
    assert a.get_bounded_region(b) == Region(first_row=1, first_col=1, rows=4, cols=4)

    a = Region(first_row=1, first_col=1, rows=4, cols=4)
    b = Region(first_row=2, first_col=2, rows=3, cols=3)
    assert a.get_bounded_region(b) == Region(first_row=2, first_col=2, rows=3, cols=3)

    a = Region(first_row=1, first_col=1, rows=4, cols=4)
    b = Region(first_row=2, first_col=2, rows=3, cols=3)
    assert a.get_bounded_region(b) == Region(first_row=2, first_col=2, rows=3, cols=3)

    a = Region(first_row=1, first_col=1, rows=4, cols=4)
    b = Region(first_row=0, first_col=2, rows=6, cols=3)
    assert a.get_bounded_region(b) == Region(first_row=1, first_col=2, rows=4, cols=3)
