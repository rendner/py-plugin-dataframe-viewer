import random

import polars as pl

rows = pl.arange(0, 130000, eager=True).to_list()
random.seed(123456789)
random.shuffle(rows)

df = pl.DataFrame({"col_" + str(c): rows for c in range(400)})

breakpoint()
