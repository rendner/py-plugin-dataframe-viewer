import numpy as np
import pandas as pd

np.random.seed(6182018)

cols = pd.Index([f'c_{i}' for i in range(5)], name="col_name")
idx = pd.Index([f'i_{i}' for i in range(5)], name="idx_name")
df = pd.DataFrame(np.random.randn(5, 5), index=idx, columns=cols)

test_case = {
    "styler": df.style.hide_columns(df.columns[1:-1]),
    "chunk_size": (2, 2),
}
