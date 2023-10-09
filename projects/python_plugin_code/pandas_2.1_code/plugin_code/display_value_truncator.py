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
from collections.abc import Sequence
from typing import Any

import numpy as np


class DisplayValueTruncator:

    def __init__(self, appr_max_chars: int, is_cell_value: bool):
        self.__char_counter: int = appr_max_chars
        self.__is_cell_value: bool = is_cell_value
        self.__inside_container_counter: int = 0
        self.__truncated_value_parts: list[str] = []

    def truncate(self, value: Any) -> str:
        self.__truncate_value_recursive(value)
        return ''.join(self.__truncated_value_parts)

    def __truncate_value_recursive(self, value: str):
        is_string = True if isinstance(value, str) else False
        if isinstance(value, tuple):
            prepend_comma = False
            self.__enter_container("(")
            for entry in value:
                if prepend_comma:
                    self.__append(", ")
                if self.__char_counter > 0:
                    self.__truncate_value_recursive(entry)
                else:
                    self.__append("...")
                    break
                prepend_comma = True
            self.__leave_container(")")
        elif isinstance(value, (Sequence, np.ndarray)) and not is_string:
            prepend_comma = False
            comma = " " if isinstance(value, np.ndarray) else ", "
            self.__enter_container("[")
            for entry in value:
                if prepend_comma:
                    self.__append(comma)
                if self.__char_counter > 0:
                    self.__truncate_value_recursive(entry)
                else:
                    self.__append("...")
                    break
                prepend_comma = True
            self.__leave_container("]")
        elif isinstance(value, dict):
            prepend_comma = False
            self.__enter_container("{")
            for k, v in value.items():
                if prepend_comma:
                    self.__append(", ")
                if self.__char_counter > 0:
                    self.__truncate_value_recursive(k)
                    self.__append(": ")
                    self.__truncate_value_recursive(v)
                else:
                    self.__append("...")
                    break
                prepend_comma = True
            self.__leave_container("}")
        else:
            value = str(value)
            if len(value) > self.__char_counter:
                if self.__char_counter <= 0:
                    value = f"{value[:1]}..."
                else:
                    value = f"{value[:self.__char_counter]}..."
            if is_string and self.__is_in_container() and self.__is_cell_value:
                value = f"'{value}'"
            self.__append(value)

    def __append(self, value: str):
        self.__truncated_value_parts.append(value)
        self.__char_counter -= len(value)

    def __enter_container(self, prefix: str):
        self.__append(prefix)
        self.__inside_container_counter += 1

    def __leave_container(self, suffix: str):
        self.__append(suffix)
        self.__inside_container_counter -= 1

    def __is_in_container(self) -> bool:
        return self.__inside_container_counter > 0
