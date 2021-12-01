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

# == copy after here ==
from typing import Callable, Tuple, Optional
from pandas._typing import Axis
from pandas.io.formats.style_render import Subset


class ApplyArgs:

    def __init__(self, args: Tuple[Callable, Optional[Axis], Optional[Subset]]):
        self.__args = args

    def func(self) -> Callable:
        return self.__args[0]

    def axis(self) -> Optional[Axis]:
        return self.__args[1]

    def subset(self) -> Optional[Subset]:
        return self.__args[2]
