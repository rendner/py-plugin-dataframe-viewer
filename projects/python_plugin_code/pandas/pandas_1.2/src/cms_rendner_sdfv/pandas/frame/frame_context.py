#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.pandas.frame.table_frame_generator import TableFrameGenerator
from cms_rendner_sdfv.pandas.shared.pandas_table_source_context import PandasTableSourceContext


class FrameContext(PandasTableSourceContext):
    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        return TableFrameGenerator(self.visible_frame)
