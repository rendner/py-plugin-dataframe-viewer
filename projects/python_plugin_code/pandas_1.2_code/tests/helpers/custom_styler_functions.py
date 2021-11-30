import numpy as np
from pandas import Series, DataFrame


def highlight_even_numbers(data, color="red"):
    attr = f'background-color: {color}'

    if isinstance(data, Series):
        return [attr if v % 2 == 0 else '' for v in data]
    elif isinstance(data, DataFrame):
        return DataFrame(np.where(data % 2 == 0, attr, ''), index=data.index, columns=data.columns)
    else:  # scalar (applymap)
        return attr if data % 2 == 0 else ''


def highlight_max_values(data, chunk_parent=None, **kwargs):
    return highlight_extrema(data, "max", kwargs.get("color", "red"), chunk_parent)


def highlight_min_values(data, chunk_parent=None, **kwargs):
    return highlight_extrema(data, "min", kwargs.get("color", "yellow"), chunk_parent)


def highlight_extrema(data, extrema, color, chunk_parent=None):
    d = data if chunk_parent is None else chunk_parent
    attr = f'background-color: {color}'

    if d.ndim == 1:  # Series from .apply(axis=0) or axis=1
        is_extrema = data == getattr(d, extrema)()
        return [attr if v else '' for v in is_extrema]
    else:  # from .apply(axis=None)
        is_extrema = data == getattr(getattr(d, extrema)(), extrema)()
        return DataFrame(np.where(is_extrema, attr, ''), index=data.index, columns=data.columns)
