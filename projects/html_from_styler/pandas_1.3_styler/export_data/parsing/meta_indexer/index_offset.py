import numpy as np
import pandas as pd

np.random.seed(6182018)
df = pd.DataFrame(np.random.randn(12, 12))

test_case = {
    "styler": df.style,
    "chunk_size": (5, 5)
}
