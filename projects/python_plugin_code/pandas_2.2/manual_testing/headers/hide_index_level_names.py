import numpy as np
from pandas import MultiIndex, DataFrame

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)
df.index.names = ["lev0", "lev1"]

styler = df.style.hide(axis="index", names=False)

breakpoint()
