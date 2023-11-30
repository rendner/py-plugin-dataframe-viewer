import polars as pl

rows = pl.arange(0, 130000, eager=True)
df = pl.DataFrame({"a": ['τ', 'a'], "b": ['π', 'b']})

breakpoint()
