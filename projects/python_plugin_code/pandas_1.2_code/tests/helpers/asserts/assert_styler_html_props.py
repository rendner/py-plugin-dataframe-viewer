#  Copyright 2022 cms.rendner (Daniel Schmidt)
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
from typing import Callable

from pandas import DataFrame
from pandas.io.formats.style import Styler

from plugin_code.html_props_validator import HTMLPropsValidator
from plugin_code.patched_styler_context import PatchedStylerContext


def create_and_assert_patched_styler_html_props(
        df: DataFrame,
        init_styler_func: Callable[[Styler], None],
        rows_per_chunk: int,
        cols_per_chunk: int,
):
    styler = df.style
    init_styler_func(styler)
    ctx = PatchedStylerContext.create(styler)

    result = HTMLPropsValidator(ctx).validate(rows_per_chunk, cols_per_chunk)
    assert result.actual == result.expected
