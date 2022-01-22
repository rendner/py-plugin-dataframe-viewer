#  Copyright 2021 cms.rendner (Daniel Schmidt)
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


def highlight_even_numbers(data, color="red"):
    attr = f'background-color: {color}'
    return np.where(data % 2 == 0, attr, None)


def highlight_max_values(data, chunk_parent=None, **kwargs):
    return highlight_extrema(data, "max", kwargs.get("color", "red"), chunk_parent)


def highlight_min_values(data, chunk_parent=None, **kwargs):
    return highlight_extrema(data, "min", kwargs.get("color", "yellow"), chunk_parent)


def highlight_extrema(data, extrema, color, chunk_parent=None):
    d = data if chunk_parent is None else chunk_parent
    attr = f'background-color: {color};'
    if extrema == "min":
        return np.where(data == np.nanmin(d.to_numpy()), attr, None)
    else:
        return np.where(data == np.nanmax(d.to_numpy()), attr, None)
