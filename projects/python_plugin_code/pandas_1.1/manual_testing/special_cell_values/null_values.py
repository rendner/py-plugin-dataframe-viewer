import numpy as np
import pandas as pd

df = pd.DataFrame({"a": [pd.NA, 1, None], "b": [np.nan, 1, -1]})

styler_1 = df.style
styler_2 = df.style.format(None, na_rep='')

breakpoint()
