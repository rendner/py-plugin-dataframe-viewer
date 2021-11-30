import numpy as np
import pandas as pd

np.random.seed(6182018)

df = pd.DataFrame(np.random.randn(50, 50))

visible_items = list(range(0, 10))

hidden_indices = df.index.delete(visible_items)
hidden_columns = df.columns.delete(visible_items)

test_case = {
    "styler": df.style.hide_index(hidden_indices).hide_columns(hidden_columns),
    "chunk_size": df.shape
}
