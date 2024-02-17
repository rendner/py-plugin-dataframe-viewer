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
import dataclasses

from cms_rendner_sdfv.base.types import TableFrame


def assert_table_frames(actual: TableFrame, expected: TableFrame, include_column_describe: bool = False):
    if not include_column_describe:
        actual = _drop_describe_from_columns(actual)
        expected = _drop_describe_from_columns(expected)
    assert actual == expected


def _drop_describe_from_columns(frame: TableFrame):
    return dataclasses.replace(
            frame,
            columns=[dataclasses.replace(cl, describe=None) for cl in frame.columns]
        )
