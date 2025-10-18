import numpy as np
import pandas as pd

np.random.seed(123456789)


# a chunk incompatible styling function
def my_highlight_max(series):
    is_max = series == series.max()
    return ['background-color: red' if cell else '' for cell in is_max]


df = pd.DataFrame(np.random.randint(1, 100, size=(2800, 4)))
styler = df.style.apply(my_highlight_max, axis='index')

breakpoint()
