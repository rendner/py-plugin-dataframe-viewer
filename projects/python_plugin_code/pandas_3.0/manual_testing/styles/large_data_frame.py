import pandas as pd
import numpy as np

np.random.seed(6182018)

df = pd.DataFrame(np.random.randn(130000, 400))


def highlight_negative(data):
    attr = 'color: red'
    if data.ndim == 1:  # Series from .apply(axis=0) or axis=1
        return [attr if v < 0 else '' for v in data]
    else:  # from .apply(axis=None)
        return pd.DataFrame(np.where(data < 0, attr, ''), index=data.index, columns=data.columns)


background_gradient_axis_0 = df.style.background_gradient(axis=0)
background_gradient_axis_1 = df.style.background_gradient(axis=1)
background_gradient_axis_none = df.style.background_gradient(axis=None)

highlight_negative_axis_0 = df.style.apply(highlight_negative, axis=0)
highlight_negative_axis_1 = df.style.apply(highlight_negative, axis=1)
highlight_negative_axis_none = df.style.apply(highlight_negative, axis=None)

breakpoint()
