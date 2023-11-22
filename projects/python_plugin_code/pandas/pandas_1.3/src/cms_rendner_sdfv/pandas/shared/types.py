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
from dataclasses import dataclass
from typing import Optional

from pandas import DataFrame, Index


@dataclass(frozen=True)
class FilterCriteria:
    index: Optional[Index] = None
    columns: Optional[Index] = None

    @staticmethod
    def from_frame(frame: Optional[DataFrame]):
        return None if frame is None else FilterCriteria(frame.index, frame.columns)

    def is_empty(self) -> bool:
        return self.index is None and self.columns is None

    def __eq__(self, other):
        if isinstance(other, FilterCriteria):
            def _equals(s: Optional[Index], o: Optional[Index]) -> bool:
                if s is None and o is None:
                    return True
                return s is not None and o is not None and s.equals(o)

            return _equals(self.columns, other.columns) and _equals(self.index, other.index)
        return False
