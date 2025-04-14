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
from typing import Any, Callable, Optional

from pandas.core.dtypes.common import (
    is_complex,
    is_float,
    is_integer,
)

from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter


class FrameValueFormatter(ValueFormatter):
    def __init__(self):
        super().__init__()
        self.__precision = min(6, self._option_or_default("display.precision", 6))
        self.__float_format: Optional[Callable] = self._option_or_default("display.float_format", None)

    def _default_format(self, x: Any, fallback_formatter) -> Any:
        if is_float(x) or is_complex(x):
            if callable(self.__float_format):
                return self.__float_format(x)
            return f"{x:.{self.__precision}f}"
        elif is_integer(x):
            return str(x)

        return fallback_formatter(x)

    def format_column(self, value: Any) -> str:
        return self._default_format(value, super().format_column)

    def format_index(self, value: Any) -> str:
        return self._default_format(value, super().format_index)

    def format_cell(self, value: Any) -> str:
        return self._default_format(value, super().format_cell)
