import numpy as np
import pandas as pd

np.random.seed(6182018)
df = pd.DataFrame(np.random.randn(12, 12))

styler = (df.style
          .format('{:+.2f}', subset=pd.IndexSlice[2:8, :])
          .background_gradient(subset=pd.IndexSlice[1:3, :])
          .highlight_max(color="red")
          .highlight_min()
          .highlight_null(color="pink"))

breakpoint()
