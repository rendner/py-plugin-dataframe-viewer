import pandas as pd

df = pd.DataFrame(columns=[f'col_{i}' for i in range(12)])

styler = df.style

breakpoint()
