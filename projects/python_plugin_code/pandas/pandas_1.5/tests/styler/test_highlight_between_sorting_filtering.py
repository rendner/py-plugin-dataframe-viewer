import pandas as pd
import pytest
from pandas import Index
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.types import TableFrameCell
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


# These tests ensure that "left" and "right" of the "highlight_between" are correctly adjusted
# when combining multiple smaller chunks.
# The expected cell styling was taken from the computed html of a Styler,
# to ensure that the generated cells have the same styling.

def _debug(styler: Styler):
    html = styler.to_html()
    return html, styler


df = pd.DataFrame.from_dict({
        'a': [0, 1, 2],
        'b': [3, 4, 5],
        'c': [6, 7, 8],
    })


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_index(combine: bool):
    ctx = PatchedStylerContext(df.style.highlight_between(
        axis='index',
        left=[0, 5, 2],
        right=[2, 8, 3],
    ))

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='1'),
            TableFrameCell(value='4'),
            TableFrameCell(value='7', css={'background-color': 'yellow'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': 'yellow'}),
            TableFrameCell(value='5'),
            TableFrameCell(value='8'),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_index__sorted(combine: bool):
    ctx = PatchedStylerContext(df.style.highlight_between(
        axis='index',
        left=[0, 5, 2],
        right=[2, 8, 3],
    ))
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[False])

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='2', css={'background-color': 'yellow'}),
            TableFrameCell(value='5'),
            TableFrameCell(value='8'),
        ],
        [
            TableFrameCell(value='1'),
            TableFrameCell(value='4'),
            TableFrameCell(value='7', css={'background-color': 'yellow'}),
        ],
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_index__column_filtered_out(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis='index',
            left=[0, 5, 2],
            right=[2, 8, 3],
        ),
        FilterCriteria(index=df.index, columns=Index(['a', 'c'])),
    )

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='1'),
            TableFrameCell(value='7', css={'background-color': 'yellow'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': 'yellow'}),
            TableFrameCell(value='8'),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_index__row_filtered_out(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis='index',
            left=[0, 5, 2],
            right=[2, 8, 3],
        ),
        FilterCriteria(index=Index([0, 2]), columns=df.columns),
    )

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='2', css={'background-color': 'yellow'}),
            TableFrameCell(value='5'),
            TableFrameCell(value='8'),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns(combine: bool):
    ctx = PatchedStylerContext(df.style.highlight_between(
        axis='columns',
        left=[0, 4, 8],
        right=[1, 6, 9],
    ))

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='1', css={'background-color': 'yellow'}),
            TableFrameCell(value='4', css={'background-color': 'yellow'}),
            TableFrameCell(value='7'),
        ],
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='5', css={'background-color': 'yellow'}),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns__sorted(combine: bool):
    ctx = PatchedStylerContext(df.style.highlight_between(
        axis='columns',
        left=[0, 4, 8],
        right=[1, 6, 9],
    ))
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[False])

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='5', css={'background-color': 'yellow'}),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': 'yellow'}),
            TableFrameCell(value='4', css={'background-color': 'yellow'}),
            TableFrameCell(value='7'),
        ],
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns__column_filtered_out(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis='columns',
            left=[0, 4, 8],
            right=[1, 6, 9],
        ),
        FilterCriteria(index=df.index, columns=Index(['a', 'c'])),
    )

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='1', css={'background-color': 'yellow'}),
            TableFrameCell(value='7'),
        ],
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns__row_filtered_out(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis='columns',
            left=[0, 4, 8],
            right=[1, 6, 9],
        ),
        FilterCriteria(index=Index([0, 2]), columns=df.columns),
    )

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': 'yellow'}),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='5', css={'background-color': 'yellow'}),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis=None,
            left=[[2, 2, 2], [1, 1, 4], [7, 7, 7]],
            right=[[2, 2, 2], [1, 4, 4], [8, 8, 8]],
        )
    )

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0'),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='1', css={'background-color': 'yellow'}),
            TableFrameCell(value='4', css={'background-color': 'yellow'}),
            TableFrameCell(value='7'),
        ],
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='5'),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none__sorted(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis=None,
            left=[[2, 2, 2], [1, 1, 4], [7, 7, 7]],
            right=[[2, 2, 2], [1, 4, 4], [8, 8, 8]],
        )
    )
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[False])

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='5'),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': 'yellow'}),
            TableFrameCell(value='4', css={'background-color': 'yellow'}),
            TableFrameCell(value='7'),
        ],
        [
            TableFrameCell(value='0'),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none__column_filtered_out(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis=None,
            left=[[2, 2, 2], [1, 1, 4], [7, 7, 7]],
            right=[[2, 2, 2], [1, 4, 4], [8, 8, 8]],
        ),
        FilterCriteria(index=df.index, columns=Index(['a', 'c'])),
    )

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0'),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='1', css={'background-color': 'yellow'}),
            TableFrameCell(value='7'),
        ],
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none__row_filtered_out(combine: bool):
    ctx = PatchedStylerContext(
        df.style.highlight_between(
            axis=None,
            left=[[2, 2, 2], [1, 1, 4], [7, 7, 7]],
            right=[[2, 2, 2], [1, 4, 4], [8, 8, 8]],
        ),
        FilterCriteria(index=Index([0, 2]), columns=df.columns),
    )

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0'),
            TableFrameCell(value='3'),
            TableFrameCell(value='6'),
        ],
        [
            TableFrameCell(value='2'),
            TableFrameCell(value='5'),
            TableFrameCell(value='8', css={'background-color': 'yellow'}),
        ],
    ]
