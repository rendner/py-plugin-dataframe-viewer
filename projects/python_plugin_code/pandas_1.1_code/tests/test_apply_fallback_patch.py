from unittest.mock import Mock

import pandas as pd
import pytest
from pandas import DataFrame, Series

from plugin_code.apply_args import ApplyArgs
from plugin_code.apply_fallback_patch import ApplyFallbackPatch
from tests.helpers.custom_styler_functions import highlight_max_values

df = pd.DataFrame.from_dict({
    "col_0": [0, 1, 2, 3, 4],
    "col_1": [5, 6, 7, 8, 9],
    "col_2": [10, 11, 12, 13, 14],
    "col_3": [15, 16, 17, 18, 19],
    "col_4": [20, 21, 22, 23, 24],
})


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
def test_correct_chunk_parent_for_subset_is_provided(axis):
    # The call signature of a mock is always (*args, **kwargs)
    # and the plugin-code will always inject the "chunk_parent" for the mock.
    # Therefore the "side_effect" has to handle "chunk_parent".
    mock = Mock(side_effect=lambda d, chunk_parent=None: highlight_max_values(d, chunk_parent=chunk_parent))

    styler = df.style

    subset = pd.IndexSlice[2:3, ["col_2", "col_3"]]
    subset_df = df.loc[subset]

    args = ApplyArgs((mock, axis, subset))
    kwargs = {}
    patch = ApplyFallbackPatch(df, args, kwargs)
    patch.apply_to_styler(styler)

    styler._compute()

    if axis is None:
        assert mock.call_count == 1
        assert isinstance(mock.call_args[1]["chunk_parent"], DataFrame)
        assert mock.call_args[1]["chunk_parent"].equals(subset_df)
    elif axis in [0, "index"]:
        assert mock.call_count == len(subset_df.index)
        for i, call in enumerate(mock.call_args_list):
            call_kwargs: dict = call[1]
            assert isinstance(call_kwargs["chunk_parent"], Series)
            assert call_kwargs["chunk_parent"].equals(subset_df.iloc[:, i])
    elif axis in [1, "columns"]:
        assert mock.call_count == len(subset_df.columns)
        for i, call in enumerate(mock.call_args_list):
            call_kwargs: dict = call[1]
            assert isinstance(call_kwargs["chunk_parent"], Series)
            assert call_kwargs["chunk_parent"].equals(subset_df.iloc[i, :])


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
def test_correct_chunk_parent_is_provided(axis):
    # The call signature of a mock is always (*args, **kwargs)
    # and the plugin-code will always inject the "chunk_parent" for the mock.
    # Therefore the "side_effect" has to handle "chunk_parent".
    mock = Mock(side_effect=lambda d, chunk_parent=None: highlight_max_values(d, chunk_parent=chunk_parent))

    styler = df.style

    args = ApplyArgs((mock, axis, None))
    patch = ApplyFallbackPatch(df, args, {})
    patch.apply_to_styler(styler)

    styler._compute()

    if axis is None:
        assert mock.call_count == 1
        assert isinstance(mock.call_args[1]["chunk_parent"], DataFrame)
        assert mock.call_args[1]["chunk_parent"].equals(df)
    elif axis in [0, "index"]:
        assert mock.call_count == len(df.index)
        for i, call in enumerate(mock.call_args_list):
            call_kwargs: dict = call[1]
            assert isinstance(call_kwargs["chunk_parent"], Series)
            assert call_kwargs["chunk_parent"].equals(df.iloc[:, i])
    elif axis in [1, "columns"]:
        assert mock.call_count == len(df.columns)
        for i, call in enumerate(mock.call_args_list):
            call_kwargs: dict = call[1]
            assert isinstance(call_kwargs["chunk_parent"], Series)
            assert call_kwargs["chunk_parent"].equals(df.iloc[i, :])


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
def test_no_chunk_parent_is_provided(axis):
    # demo that providing non requested "chunk_parent" would raise
    highlight_without_chunk_parent = lambda x: highlight_max_values(x)
    msg = "got an unexpected keyword argument 'chunk_parent'"
    with pytest.raises(TypeError, match=msg):
        highlight_without_chunk_parent(df, chunk_parent=df)

    # the real test
    styler = df.style

    args = ApplyArgs((highlight_without_chunk_parent, axis, None))
    patch = ApplyFallbackPatch(df, args, {})
    patch.apply_to_styler(styler)

    styler._compute()


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
def test_chunk_parent_is_provided_for_keyword_arg(axis):
    styler = df.style

    args = ApplyArgs((lambda d, chunk_parent=None: highlight_max_values(d, chunk_parent), axis, None))
    patch = ApplyFallbackPatch(df, args, {})
    patch.apply_to_styler(styler)

    styler._compute()


@pytest.mark.parametrize("axis", [None, 0, 1, "index", "columns"])
def test_chunk_parent_is_provided_for_kwargs(axis):
    styler = df.style

    args = ApplyArgs((lambda d, **kwargs: highlight_max_values(d, **kwargs), axis, None))
    patch = ApplyFallbackPatch(df, args, {})
    patch.apply_to_styler(styler)

    styler._compute()

