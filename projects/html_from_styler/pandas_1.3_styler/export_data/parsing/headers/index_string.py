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
    "create_styler": lambda: df.style,
    "chunk_size": (2, 2)
}
