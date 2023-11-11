import numpy as np
from pandas import MultiIndex, DataFrame

np.random.seed(123456)

midx = MultiIndex.from_product([["x", "y"], ["a", "b", "c"]])
df = DataFrame(np.random.randn(6, 6), index=midx, columns=midx)

styler = df.style.hide(level=1)

breakpoint()
