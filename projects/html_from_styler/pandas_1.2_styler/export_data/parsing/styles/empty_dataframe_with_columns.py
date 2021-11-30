import pandas as pd

df = pd.DataFrame(columns=[f'col_{i}' for i in range(12)])

test_case = {
    "styler": df.style,
    "chunk_size": (5, 5)
}
