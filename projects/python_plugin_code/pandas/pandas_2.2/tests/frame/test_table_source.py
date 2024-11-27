import numpy as np
import pandas as pd

from cms_rendner_sdfv.base.types import TableFrame, TableFrameCell, TableStructure, TableStructureColumnInfo, \
    TableStructureColumn, TableInfo, TableStructureLegend, TableSourceKind
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.frame.table_source import TableSource

np.random.seed(123456)

midx_rows = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['rows-char', 'rows-color'])
midx_cols = pd.MultiIndex.from_product([["x", "y"], ["a", "b", "c"]], names=['cols-char', 'cols-color'])
multi_df = pd.DataFrame(np.arange(0, 36).reshape(6, 6), index=midx_rows, columns=midx_cols)


def test_compute_chunk_table_frame():
    ts = TableSource(FrameContext(multi_df), "finger-1")
    actual = ts.compute_chunk_table_frame(0, 0, 2, 2)

    assert actual == ts.serialize(TableFrame(
        index_labels=[['x', 'a'], ['x', 'b']],
        cells=[
            [TableFrameCell(value='0', css=None), TableFrameCell(value='1', css=None)],
            [TableFrameCell(value='6', css=None), TableFrameCell(value='7', css=None)],
        ],
    ))


def test_table_info():
    ts = TableSource(FrameContext(multi_df), "finger-1")

    assert ts.get_info() == ts.serialize(
        TableInfo(
            kind=TableSourceKind.TABLE_SOURCE.name,
            structure=TableStructure(
                org_rows_count=6,
                org_columns_count=6,
                rows_count=6,
                columns_count=6,
                fingerprint="finger-1",
                column_info=TableStructureColumnInfo(
                    legend=TableStructureLegend(index=['rows-char', 'rows-color'], column=['cols-char', 'cols-color']),
                    columns=[
                        TableStructureColumn(dtype='int64', labels=['x', 'a'], id=0),
                        TableStructureColumn(dtype='int64', labels=['x', 'b'], id=1),
                        TableStructureColumn(dtype='int64', labels=['x', 'c'], id=2),
                        TableStructureColumn(dtype='int64', labels=['y', 'a'], id=3),
                        TableStructureColumn(dtype='int64', labels=['y', 'b'], id=4),
                        TableStructureColumn(dtype='int64', labels=['y', 'c'], id=5)
                    ],
                )
            ),
        )
    )
