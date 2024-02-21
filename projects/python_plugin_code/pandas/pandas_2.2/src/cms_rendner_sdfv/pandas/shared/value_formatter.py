#  Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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

from cms_rendner_sdfv.base.constants import CELL_MAX_STR_LEN
from cms_rendner_sdfv.base.helpers import truncate_str


class ValueFormatter:

    @staticmethod
    def format_column(value: Any) -> str:
        return value if isinstance(value, str) else pprint_thing(value)

    @staticmethod
    def format_index(value: Any) -> str:
        return value if isinstance(value, str) else pprint_thing(value)

    @staticmethod
    def format_cell(value: Any) -> str:
        v = value
        if not isinstance(v, str):
            max_seq_items = None
            try:
                max_seq_items = get_option("display.max_seq_items", True)
            except OptionError:
                pass
            v = pprint_thing(v, max_seq_items=max_seq_items or 42)
        return truncate_str(v, CELL_MAX_STR_LEN)
