import pandas as pd

data_dict = {'col_1': [3, 2, 1, 0], 'col_2': ['a', 'b', 'c', 'd']}

df = pd.DataFrame.from_dict(data_dict)


def loop_with_breakpoints():
    for i in range(2):
        breakpoint()


loop_with_breakpoints()
breakpoint()
