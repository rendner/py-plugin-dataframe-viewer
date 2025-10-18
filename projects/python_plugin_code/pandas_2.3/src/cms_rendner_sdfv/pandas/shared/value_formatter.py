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

from pandas.errors import OptionError
from pandas.io.formats.printing import pprint_thing, get_option

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN, COL_STATISTIC_ENTRY_MAX_STR_LEN, CELL_MAX_LIST_LEN
from cms_rendner_sdfv.base.helpers import truncate_str


class ValueFormatter:
    def __init__(self):
        self.__display_max_seq_items = min(CELL_MAX_LIST_LEN, self._option_or_default("display.max_seq_items", CELL_MAX_LIST_LEN))

    @staticmethod
    def _option_or_default(key: str, default: Any):
        try:
            return get_option(key, True)
        except OptionError:
            return default

    @staticmethod
    def format_column(value: Any) -> str:
        return value if isinstance(value, str) else pprint_thing(value)

    @staticmethod
    def format_index(value: Any) -> str:
        return value if isinstance(value, str) else pprint_thing(value)

    @staticmethod
    def format_column_statistic_entry(value: Any) -> str:
        v = value if isinstance(value, str) else pprint_thing(value, max_seq_items=10)
        return truncate_str(v, COL_STATISTIC_ENTRY_MAX_STR_LEN)

    def format_cell(self, value: Any) -> str:
        v = value
        if not isinstance(v, str):
            v = pprint_thing(v, max_seq_items=self.__display_max_seq_items)
        return truncate_str(v, CELL_MAX_STR_LEN)
