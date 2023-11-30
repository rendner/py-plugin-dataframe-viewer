#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
from hashlib import blake2b
from typing import Any

from polars import DataFrame


def create_fingerprint(frame: DataFrame, org_data_source: Any = None) -> str:
    # A "fingerprint" is generated to help to identify if two data-frame instances are created with the
    # same data source. Two objects with non-overlapping lifetimes may have the same id() value.
    # Such a scenario can be simulated with the following minimal example:
    #
    # used_ids = set()
    # for x in range(100):
    #     my_id = id(pl.DataFrame())
    #     assert my_id not in used_ids
    #     used_ids.add(my_id)
    #
    # Therefore, additional data is included to create a better fingerprint.
    fingerprint_input = [
        id(org_data_source if org_data_source is not None else frame),
        frame.shape,
        frame.columns[:60],
        frame.dtypes[:60]
    ]
    return blake2b('-'.join(str(x) for x in fingerprint_input).encode(), digest_size=16).hexdigest()
