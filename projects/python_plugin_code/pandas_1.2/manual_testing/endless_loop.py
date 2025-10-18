import pandas as pd
import numpy as np
import time

np.random.seed(6182018)

df = pd.DataFrame(np.random.randn(1300, 40))

# step over after viewer is opened to test with a resumed debug process
breakpoint()

while True:
    time.sleep(4)
    breakpoint()
