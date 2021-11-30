import pandas as pd

df = pd.DataFrame.from_dict({
    "col_0": [0,    1,  2,  3,  4],
    "col_1": [5,    6,  7,  8,  9],
    "col_2": [10,  11, 12, 13, 14],
    "col_3": [15,  16, 17, 18, 19],
    "col_4": [20,  21, 22, 23, 24],
})

df.index = ["Apples", "Oranges", "Puppies", "Ducks", "Cats"]

test_case = {
    "styler": df.style,
    "chunk_size": (2, 2)
}
