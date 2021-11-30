import pandas as pd

df = pd.DataFrame.from_dict({
    0: [0, 1, 2, 3, 4],
    1: [5, 6, 7, 8, 9],
    2: [10, 11, 12, 13, 14],
    3: [15, 16, 17, 18, 19],
    4: [20, 21, 22, 23, 24],
})

test_case = {
    "styler": df.style,
    "chunk_size": (2, 2),
}
