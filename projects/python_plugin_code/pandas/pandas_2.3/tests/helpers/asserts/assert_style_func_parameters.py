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
import inspect
from typing import Callable


def assert_style_func_parameters(styling_func: Callable, known_params: [str]):
    unused_params = known_params.copy()
    new_params = []

    sig = inspect.signature(styling_func)
    for param in sig.parameters.values():
        if param.name not in known_params:
            new_params.append(param.name)
        if param.name in unused_params:
            unused_params.remove(param.name)

    assert new_params == [], f'new styling parameters found: {new_params}'
    assert unused_params == [], f'unused styling parameters found: {unused_params}'
