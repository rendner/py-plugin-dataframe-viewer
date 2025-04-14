from typing import Any, Dict, Tuple

import pytest

from cms_rendner_sdfv.base.table_source import AbstractMetaComputer
from cms_rendner_sdfv.base.types import CellMeta


class TestMetaComputer(AbstractMetaComputer):
    def __init__(self, source: Dict[int, Tuple[Any, Any]]):
        super().__init__()
        self.__source = source

    def _compute_min_max_at(self, col: int) -> (Any, Any):
        return self.__source[col]


@pytest.mark.parametrize(
    "vmin, vmax",
    [
        (1, 2),
        (0.5, 4.567),
        (0 + 3j, 2 + 3j)
    ],
)
def test_compute_cell_meta(vmin, vmax):
    mc = TestMetaComputer({0: (vmin, vmax)})

    actual = mc.compute_cell_meta(0, vmin)
    assert actual == CellMeta(is_min=True, cmap_value=0).pack()

    actual = mc.compute_cell_meta(0, vmax)
    assert actual == CellMeta(is_max=True, cmap_value=100000).pack()


@pytest.mark.parametrize(
    "v, vmin, vmax",
    [
        (0.5, float('-inf'), 10.2),
        (0.5, 10.2, float('inf')),
        (0 + 3j, 2 + 3j, float('inf') + 0j),
    ])
def test_compute_cell_meta_with_infinity(v, vmin, vmax):
    mc = TestMetaComputer({0: (vmin, vmax)})

    actual = mc.compute_cell_meta(0, v)
    assert actual == CellMeta(cmap_value=-1).pack()

    actual = mc.compute_cell_meta(0, vmin)
    assert actual == CellMeta(is_min=True, cmap_value=-1).pack()

    actual = mc.compute_cell_meta(0, vmax)
    assert actual == CellMeta(is_max=True, cmap_value=-1).pack()


def test_with_nan():
    mc = TestMetaComputer({0: (0, 0)})

    actual = mc.compute_cell_meta(0, float('nan'))
    assert actual == CellMeta.nan().pack()


def test_compute_cell_meta_works_if__is_nan__raises():
    class RaisingTestMetaComputer(TestMetaComputer):
        def _is_nan(self, v: Any) -> bool:
            raise Exception('error')

    mc = RaisingTestMetaComputer({0: (0, 0)})

    actual = mc.compute_cell_meta(0, 0)
    assert actual == CellMeta.min_max().pack()


def test_compute_cell_meta_works_if__compute_min_max_at__raises():
    class RaisingTestMetaComputer(TestMetaComputer):
        def _compute_min_max_at(self, col: int) -> (Any, Any):
            raise Exception('error')

    mc = RaisingTestMetaComputer({0: (0, 0)})

    actual = mc.compute_cell_meta(0, 0)
    assert actual is None

