import numpy as np
import pandas as pd

df = pd.DataFrame({"a": [pd.NA, 1, None], "b": [np.nan, 1, -1]})

test_case = {
    "styler": df.style.format(None, na_rep='').highlight_min(),
    "chunk_size": (1, 2),
}
