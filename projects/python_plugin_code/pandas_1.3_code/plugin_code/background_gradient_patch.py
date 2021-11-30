# source: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L1826-L1978
# source: https://github.com/pandas-dev/pandas/blob/v1.3.0/pandas/io/formats/style.py#L2722-L2786
from plugin_code.apply_args import ApplyArgs
from plugin_code.base_apply_patcher import BaseApplyPatcher

# == copy after here ==
import numpy as np
import pandas as pd
from pandas.io.formats.style import _validate_apply_axis_arg


class BackgroundGradientPatch(BaseApplyPatcher):

    def __init__(self, data: pd.DataFrame, apply_args: ApplyArgs, func_kwargs: dict):
        BaseApplyPatcher.__init__(self, data, apply_args, func_kwargs)

    def _exec_patched_func(self, chunk: pd.DataFrame):

        # "gmap":
        #
        # Gradient map for determining the background colors.
        # If not supplied will use the underlying data from rows, columns or frame.
        # If given as an ndarray or list-like must be an identical shape to the underlying data considering
        # axis and subset. If given as DataFrame or Series must have same index and column labels considering
        # axis and subset. If supplied, vmin and vmax should be given relative to this gradient map.

        vmin = self._func_kwargs.get("vmin", None)
        vmax = self._func_kwargs.get("vmax", None)
        gmap = self._func_kwargs.get("gmap", None)

        chunk_parent = self._get_parent(chunk)

        if gmap is None:
            gmap = chunk_parent.to_numpy(dtype=float)
        else:
            gmap = _validate_apply_axis_arg(gmap, "gmap", float, chunk_parent)

        if vmin is None or vmax is None:
            if vmin is None:
                vmin = np.nanmin(gmap)
            if vmax is None:
                vmax = np.nanmax(gmap)

        # adjust shape of gmap to match shape of chunk
        if chunk.empty:
            gmap = chunk
        # Note:
        # "get_indexer_for" has to be used.
        # Using "first_row, first_column, last_row, last_column" which were used to create the chunk, like:
        #
        # (gmap is a DataFrame)
        # "gmap = gmap.iloc[self._first_row:self._last_row, self._first_column:self._last_column]"
        #
        # will not work, because a user could have specified a subset. The coordinates of the subset
        # have to be taken into account to adjust the gmap correctly. This is all done automatically
        # by using "get_indexer_for" without the need to access the "first_row, first_column, last_row, last_column".
        elif isinstance(chunk, pd.Series):
            gmap = gmap[chunk_parent.index.get_indexer_for(chunk.index)]
        elif isinstance(chunk, pd.DataFrame) and self._apply_args.axis() is None:
            ri = chunk_parent.index.get_indexer_for(chunk.index)
            ci = chunk_parent.columns.get_indexer_for(chunk.columns)
            if isinstance(gmap, pd.DataFrame):
                gmap = gmap.iloc[(ri, ci)]
            elif isinstance(gmap, np.ndarray):
                gmap = pd.DataFrame(data=gmap, index=chunk_parent.index, columns=chunk_parent.columns)
                gmap = gmap.iloc[(ri, ci)]

        return self._apply_args.func()(chunk, **dict(self._func_kwargs, vmin=vmin, vmax=vmax, gmap=gmap))
