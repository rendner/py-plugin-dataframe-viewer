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

from pandas import DataFrame
from pandas.io.formats.style import Styler


class StyledDataFrameViewerBridge:

    @classmethod
    def create_patched_styler(cls,
                              frame_or_styler: Union[DataFrame, Styler],
                              filter_frame: Optional[DataFrame] = None,
                              ) -> PatchedStyler:
        if isinstance(frame_or_styler, DataFrame):
            styler: Styler = frame_or_styler.style
        else:
            styler: Styler = frame_or_styler

        return PatchedStyler(PatchedStylerContext.create(styler, FilterCriteria.from_frame(filter_frame)))

    @staticmethod
    def check() -> bool:
        return True
