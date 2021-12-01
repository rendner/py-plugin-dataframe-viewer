#  Copyright 2021 cms.rendner (Daniel Schmidt)
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
from plugin_code.apply_args import ApplyArgs
from plugin_code.apply_map_args import ApplyMapArgs

# == copy after here ==
from typing import Tuple, Callable, cast, Optional
from pandas._typing import Axis
from pandas.io.formats.style_render import Subset


class ExportedStyle:
    def __init__(self, args: Tuple[Callable, tuple, dict]):
        self.__args = args

    def apply_func(self) -> Callable:
        return self.__args[0]

    def apply_args(self) -> tuple:
        return self.__args[1]

    def apply_args_func(self) -> Callable:
        return self.__args[1][0]

    def create_apply_args(self) -> ApplyArgs:
        return ApplyArgs(cast(Tuple[Callable, Optional[Axis], Optional[Subset]], self.__args[1]))

    def create_apply_map_args(self) -> ApplyMapArgs:
        return ApplyMapArgs(cast(Tuple[Callable, Optional[Subset]], self.__args[1]))

    def apply_kwargs(self) -> dict:
        return self.__args[2]
