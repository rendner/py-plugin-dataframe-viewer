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

# == copy after here ==
from typing import Union

from pandas import DataFrame
from pandas.io.formats.style import Styler


# This bridge hides all created "patchedStyler" instances, to not pollute the PyCharm
# debugger view, by collecting them in an internal list.
class StyledDataFrameViewerBridge:
    patched_styler_refs = []

    @classmethod
    def create_patched_styler(cls, frame_or_styler: Union[DataFrame, Styler]) -> PatchedStyler:
        p = PatchedStyler(frame_or_styler.style) if isinstance(frame_or_styler, DataFrame) else PatchedStyler(
            frame_or_styler)
        cls.patched_styler_refs.append(p)
        return p

    @classmethod
    def delete_patched_styler(cls, patched_styler: PatchedStyler):
        cls.patched_styler_refs.remove(patched_styler)

    @classmethod
    def delete_all(cls):
        cls.patched_styler_refs.clear()

    @staticmethod
    def check() -> bool:
        return True
