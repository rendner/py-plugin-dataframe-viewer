import pandas as pd

df = pd.DataFrame.from_dict({
    ("A", "col_0"): [0, 1, 2, 3, 4, 5],
    ("A", "col_1"): [6, 7, 8, 9, 10, 11],
    ("B", "col_2"): [12, 13, 14, 15, 16, 17],
    ("B", "col_3"): [18, 19, 20, 21, 22, 23],
    ("C", "col_4"): [24, 25, 26, 27, 28, 29],
})

chars = ["X", "Y", "Z"]
colors = ['green', 'purple']
df.index = pd.MultiIndex.from_product([chars, colors], names=['char', 'color'])

styler = df.style

breakpoint()

