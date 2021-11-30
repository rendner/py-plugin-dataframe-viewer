import pandas as pd
import numpy as np

np.random.seed(123456)

# hierarchical indices and columns
index = pd.MultiIndex.from_product([[2013, 2014], [1, 2]], names=['year', 'visit'])
columns = pd.MultiIndex.from_product([['Bob', 'Guido', 'Sue'], ['HR', 'Temp']], names=['subject', 'type'])
data = np.round(np.random.randn(4, 6), 1)

df = pd.DataFrame(data, index=index, columns=columns)

test_case = {
    "styler": df.style,
    "chunk_size": (2, 2)
}

