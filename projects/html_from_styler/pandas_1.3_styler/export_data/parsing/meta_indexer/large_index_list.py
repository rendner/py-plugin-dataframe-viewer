import numpy as np
import pandas as pd

np.random.seed(6182018)

size: int = 200
df = pd.DataFrame(np.random.randn(size, size))

visible_items = []
for i in range(0, size, 100):
    visible_items.extend(list(range(i, i + 20)))

hidden_indices = df.index.delete(visible_items)
hidden_columns = df.columns.delete(visible_items)

test_case = {
    "styler": df.style.hide_index(hidden_indices).hide_columns(hidden_columns),
    "chunk_size": df.shape
}
