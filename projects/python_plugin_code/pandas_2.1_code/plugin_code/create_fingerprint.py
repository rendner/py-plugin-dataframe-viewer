#  Copyright 2023 cms.rendner (Daniel Schmidt)
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

# == copy after here ==
from typing import Union, Any
from hashlib import blake2b
from pandas import DataFrame
from pandas.io.formats.style import Styler


def create_fingerprint(frame_or_styler: Union[DataFrame, Styler], org_data_source: Any = None) -> str:
    frame = frame_or_styler if isinstance(frame_or_styler, DataFrame) else frame_or_styler.data
    # A "fingerprint" is generated to help to identify if two patched styler instances are created with the
    # same data source. Two objects with non-overlapping lifetimes may have the same id() value.
    # Such a scenario can be simulated with the following minimal example:
    #
    # for x in range(100):
    #   assert id(pd.DataFrame()) != id(pd.DataFrame())
    #
    # Therefore, additional data is included to create a better fingerprint.
    #
    # dtypes also include the column labels - therefore, we don't have to include frame.columns[:60]
    fingerprint_input = [
        id(org_data_source if org_data_source is not None else frame),
        frame.shape,
        frame.index[:60],
        frame.dtypes[:60]
    ]
    return blake2b('-'.join(str(x) for x in fingerprint_input).encode(), digest_size=16).hexdigest()