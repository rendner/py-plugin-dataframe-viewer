import numpy as np


def highlight_even_numbers(data, color="red"):
    attr = f'background-color: {color}'
    return np.where(data % 2 == 0, attr, None)


def highlight_max_values(data, chunk_parent=None, **kwargs):
    return highlight_extrema(data, "max", kwargs.get("color", "red"), chunk_parent)


def highlight_min_values(data, chunk_parent=None, **kwargs):
    return highlight_extrema(data, "min", kwargs.get("color", "yellow"), chunk_parent)


def highlight_extrema(data, extrema, color, chunk_parent=None):
    d = data if chunk_parent is None else chunk_parent
    attr = f'background-color: {color};'
    if extrema is "min":
        return np.where(data == np.nanmin(d.to_numpy()), attr, None)
    else:
        return np.where(data == np.nanmax(d.to_numpy()), attr, None)
