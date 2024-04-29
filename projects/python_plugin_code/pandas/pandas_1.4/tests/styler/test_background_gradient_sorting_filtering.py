import pandas as pd
import pytest
from pandas import Index
from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.types import TableFrameCell
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext

# These tests ensure that the "gmap" of the "background_gradient" is correctly adjusted
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
    ctx = PatchedStylerContext(df.style.background_gradient(axis='index'))

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#fff7fb', 'color': '#000000'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='4', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='7', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            TableFrameCell(value='5', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_index__sorted(combine: bool):
    ctx = PatchedStylerContext(df.style.background_gradient(axis='index'))
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[False])

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='2', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            TableFrameCell(value='5', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='4', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='7', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#fff7fb', 'color': '#000000'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_index__column_filtered_out(combine: bool):
    filter_criteria = FilterCriteria(index=df.index, columns=Index(['a', 'c']))
    ctx = PatchedStylerContext(df.style.background_gradient(axis='index'), filter_criteria)

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#fff7fb', 'color': '#000000'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='7', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_index__row_filtered_out(combine: bool):
    filter_criteria = FilterCriteria(index=Index([0, 2]), columns=df.columns)
    ctx = PatchedStylerContext(df.style.background_gradient(axis='index'), filter_criteria)

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#fff7fb', 'color': '#000000'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            TableFrameCell(value='5', css={'background-color': '#023858', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns(combine: bool):
    ctx = PatchedStylerContext(df.style.background_gradient(axis='columns'))

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='6', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='4', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='7', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='5', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns__sorted(combine: bool):
    ctx = PatchedStylerContext(df.style.background_gradient(axis='columns'))
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[False])

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='2', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='5', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='4', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='7', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='6', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns__column_filtered_out(combine: bool):
    filter_criteria = FilterCriteria(index=df.index, columns=Index(['a', 'c']))
    ctx = PatchedStylerContext(df.style.background_gradient(axis='columns'), filter_criteria)

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='7', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_columns__row_filtered_out(combine: bool):
    filter_criteria = FilterCriteria(index=Index([0, 2]), columns=df.columns)
    ctx = PatchedStylerContext(df.style.background_gradient(axis='columns'), filter_criteria)

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='6', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='5', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none(combine: bool):
    ctx = PatchedStylerContext(df.style.background_gradient(axis=None))

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#a5bddb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#056faf', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#ece7f2', 'color': '#000000'}),
            TableFrameCell(value='4', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='7', css={'background-color': '#04598c', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#d0d1e6', 'color': '#000000'}),
            TableFrameCell(value='5', css={'background-color': '#358fc0', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none__sorted(combine: bool):
    ctx = PatchedStylerContext(df.style.background_gradient(axis=None))
    ctx.set_sort_criteria(sort_by_column_index=[0], sort_ascending=[False])

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='2', css={'background-color': '#d0d1e6', 'color': '#000000'}),
            TableFrameCell(value='5', css={'background-color': '#358fc0', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#ece7f2', 'color': '#000000'}),
            TableFrameCell(value='4', css={'background-color': '#73a9cf', 'color': '#f1f1f1'}),
            TableFrameCell(value='7', css={'background-color': '#04598c', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#a5bddb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#056faf', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none__column_filtered_out(combine: bool):
    filter_criteria = FilterCriteria(index=df.index, columns=Index(['a', 'c']))
    ctx = PatchedStylerContext(df.style.background_gradient(axis=None), filter_criteria)

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#056faf', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='1', css={'background-color': '#ece7f2', 'color': '#000000'}),
            TableFrameCell(value='7', css={'background-color': '#04598c', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#d0d1e6', 'color': '#000000'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]


@pytest.mark.parametrize("combine", [True, False])
def test_cells__axis_none__row_filtered_out(combine: bool):
    filter_criteria = FilterCriteria(index=Index([0, 2]), columns=df.columns)
    ctx = PatchedStylerContext(df.style.background_gradient(axis=None), filter_criteria)

    generator = ctx.get_table_frame_generator()
    actual = generator.generate_by_combining_chunks(1, 1) if combine else generator.generate()

    assert actual.cells == [
        [
            TableFrameCell(value='0', css={'background-color': '#fff7fb', 'color': '#000000'}),
            TableFrameCell(value='3', css={'background-color': '#a5bddb', 'color': '#000000'}),
            TableFrameCell(value='6', css={'background-color': '#056faf', 'color': '#f1f1f1'}),
        ],
        [
            TableFrameCell(value='2', css={'background-color': '#d0d1e6', 'color': '#000000'}),
            TableFrameCell(value='5', css={'background-color': '#358fc0', 'color': '#f1f1f1'}),
            TableFrameCell(value='8', css={'background-color': '#023858', 'color': '#f1f1f1'}),
        ],
    ]
