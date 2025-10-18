#  Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
from typing import Any

from pandas import DataFrame, Series, isna
from pandas.api.types import is_numeric_dtype
from pandas.core.dtypes.common import is_bool_dtype

from cms_rendner_sdfv.base.table_source import AbstractMetaComputer


class MetaComputer(AbstractMetaComputer):
    def __init__(self, source_frame: DataFrame):
        super().__init__()
        self.__source_frame = source_frame

    def _is_nan(self, v: Any) -> bool:
        return isna(v)

    def unlink(self):
        self.__source_frame = None

    def _compute_min_max_at(self, col: int) -> (Any, Any):
        column: Series = self.__source_frame.iloc[:, col]
        if is_numeric_dtype(column.dtype) and not is_bool_dtype(column.dtype):
            return column.min(), column.max()
        return None, None
