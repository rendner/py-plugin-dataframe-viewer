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
df = pd.DataFrame(np.random.randn(12, 12))

test_case = {
    "create_styler": lambda: (df.style
                              .format('{:+.2f}', subset=pd.IndexSlice[2:8, :])
                              .background_gradient(subset=pd.IndexSlice[1:3, :])
                              .highlight_max(color="red")
                              .highlight_min()
                              .highlight_null(null_color="pink")),
    "chunk_size": (5, 5)
}
