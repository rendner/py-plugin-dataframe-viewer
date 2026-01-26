import numpy as np
import pandas as pd

np.random.seed(6182018)
df = pd.DataFrame(np.random.randn(5, 5))

styler = df.style.hide(axis="index").hide(axis="columns")

breakpoint()
