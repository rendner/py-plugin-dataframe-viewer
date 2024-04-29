import polars as pl

# https://github.com/pola-rs/polars/issues/10646
DATA = {'text': ['nospace', '     before', 'after     ', '     both     ', 'bet     ween']}
df = pl.DataFrame(data=DATA)

breakpoint()
