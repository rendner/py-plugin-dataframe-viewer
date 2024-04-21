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
from typing import Optional

from pandas import DataFrame

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo
from cms_rendner_sdfv.pandas.styler.todo_patcher import TodoPatcher


# map: https://github.com/pandas-dev/pandas/blob/v2.1.0/pandas/io/formats/style.py#L2035-L2092
# applymap: https://github.com/pandas-dev/pandas/blob/v2.1.0/pandas/io/formats/style.py#L2095-L2122
class MapPatcher(TodoPatcher):

    def __init__(self, org_frame: DataFrame, todo: StylerTodo):
        super().__init__(org_frame, todo)

    def create_patched_todo(self, chunk: DataFrame) -> Optional[StylerTodo]:
        return self._todo_builder(chunk).build()
