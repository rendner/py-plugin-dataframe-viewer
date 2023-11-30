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
from typing import Any, Union

import polars as pl

from cms_rendner_sdfv.base.table_source import AbstractTableSource, AbstractTableSourceFactory
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure
from cms_rendner_sdfv.polars.create_fingerprint import create_fingerprint
from cms_rendner_sdfv.polars.frame_context import FrameContext
from cms_rendner_sdfv.polars.table_source import TableSource


class TableSourceFactory(AbstractTableSourceFactory):

    def _create_internal(self,
                         data_source: Any,
                         config: CreateTableSourceConfig,
                         caller_globals: dict,
                         ) -> Union[AbstractTableSource, CreateTableSourceFailure]:
        ds_frame = None
        if isinstance(data_source, dict):
            ds_frame = pl.from_dict(data_source)
        elif isinstance(data_source, pl.DataFrame):
            ds_frame = data_source
        else:
            return CreateTableSourceFailure(error_kind="UNSUPPORTED_DATA_SOURCE_TYPE", info=str(type(data_source)))

        pre_fingerprint = config.previous_fingerprint
        cur_fingerprint = create_fingerprint(ds_frame, data_source)
        if pre_fingerprint is not None and pre_fingerprint != cur_fingerprint:
            return CreateTableSourceFailure(error_kind="INVALID_FINGERPRINT", info=cur_fingerprint)

        return TableSource(FrameContext(ds_frame), fingerprint=cur_fingerprint)
