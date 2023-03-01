#  Copyright 2022 cms.rendner (Daniel Schmidt)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import numpy as np
import pandas as pd

np.random.seed(6182018)

cols = pd.Index([f'c_{i}' for i in range(5)], name="col_name")
idx = pd.Index([f'i_{i}' for i in range(5)], name="idx_name")
df = pd.DataFrame(np.random.randn(5, 5), index=idx, columns=cols)

test_case = {
    "create_styler": lambda: df.style.hide(axis="columns"),
    "chunk_size": (2, 2),
}
