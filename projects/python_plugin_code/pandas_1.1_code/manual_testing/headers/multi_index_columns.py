import pandas as pd

df = pd.DataFrame.from_dict({
    ("A", "col_0"): [0,    1,  2,  3,  4],
    ("A", "col_1"): [5,    6,  7,  8,  9],
    ("B", "col_2"): [10,  11, 12, 13, 14],
    ("B", "col_3"): [15,  16, 17, 18, 19],
    ("C", "col_4"): [20,  21, 22, 23, 24],
})

styler = df.style

breakpoint()
