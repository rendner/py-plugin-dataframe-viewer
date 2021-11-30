import numpy as np
import pandas as pd

np.random.seed(6182018)
df = pd.DataFrame(np.random.randn(5, 5))

test_case = {
    "styler": df.style.hide_index(),
    "chunk_size": (2, 2),
}
