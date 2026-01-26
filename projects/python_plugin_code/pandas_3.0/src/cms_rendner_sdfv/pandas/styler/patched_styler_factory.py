#  Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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

from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.exceptions import TableSourceException, TableSourceErrorKind
from cms_rendner_sdfv.base.table_source import AbstractTableSource, AbstractTableSourceFactory
from cms_rendner_sdfv.base.types import CreateTableSourceConfig
from cms_rendner_sdfv.pandas.styler.patched_styler import PatchedStyler
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext


class PatchedStylerFactory(AbstractTableSourceFactory):

    def _create_internal(self,
                         data_source: Any,
                         config: CreateTableSourceConfig,
                         ) -> AbstractTableSource:

        if not isinstance(data_source, Styler):
            raise TableSourceException(
                error_kind=TableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
                info=str(type(data_source)),
            )

        return PatchedStyler(PatchedStylerContext(data_source))
