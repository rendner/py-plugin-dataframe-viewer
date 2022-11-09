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
from plugin_code.patched_styler import PatchedStyler
from plugin_code.patched_styler_context import PatchedStylerContext, FilterCriteria

# == copy after here ==
from typing import Union, Optional
from hashlib import blake2b
from pandas import DataFrame
from pandas.io.formats.style import Styler


class StyledDataFrameViewerBridge:

    @staticmethod
    def create_patched_styler(frame_or_styler: Union[DataFrame, Styler],
                              filter_frame: Optional[DataFrame] = None,
                              ) -> PatchedStyler:
        if isinstance(frame_or_styler, DataFrame):
            styler: Styler = frame_or_styler.style
        else:
            styler: Styler = frame_or_styler

        return PatchedStyler(PatchedStylerContext.create(styler, FilterCriteria.from_frame(filter_frame)))

    @staticmethod
    def create_fingerprint(frame_or_styler: Union[DataFrame, Styler]) -> str:
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
        fingerprint_input = [id(frame), frame.shape, frame.index[:60], frame.dtypes[:60]]
        return blake2b('-'.join(str(x) for x in fingerprint_input).encode(), digest_size=16).hexdigest()
