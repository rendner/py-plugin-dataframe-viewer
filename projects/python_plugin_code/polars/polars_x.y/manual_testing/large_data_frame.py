import polars as pl

rows = pl.arange(0, 130000, eager=True)
df = pl.DataFrame({"col_" + str(c): rows for c in range(400)})

breakpoint()
