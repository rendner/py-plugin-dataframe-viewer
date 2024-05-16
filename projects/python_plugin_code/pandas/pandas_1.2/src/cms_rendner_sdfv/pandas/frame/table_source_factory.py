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
from typing import Any, Union

from pandas import DataFrame

from cms_rendner_sdfv.base.table_source import AbstractTableSource, AbstractTableSourceFactory
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, CreateTableSourceErrorKind
from cms_rendner_sdfv.pandas.frame.table_source import TableSource
from cms_rendner_sdfv.pandas.frame.frame_context import FrameContext
from cms_rendner_sdfv.pandas.shared.create_fingerprint import create_fingerprint
from cms_rendner_sdfv.pandas.shared.types import FilterCriteria


class TableSourceFactory(AbstractTableSourceFactory):

    def _create_internal(self,
                         data_source: Any,
                         config: CreateTableSourceConfig,
                         caller_globals: dict,
                         ) -> Union[AbstractTableSource, CreateTableSourceFailure]:
        ds_frame = None
        if isinstance(data_source, dict):
            if config.data_source_transform_hint == "DictKeysAsRows":
                ds_frame = DataFrame.from_dict(data_source, orient='index')
            else:
                try:
                    ds_frame = DataFrame.from_dict(data_source, orient='columns')
                except ValueError as e:
                    # fix if dict is not of the form {field : array-like} or {field : dict}
                    # https://github.com/pandas-dev/pandas/issues/12387
                    if str(e) == "If using all scalar values, you must pass an index":
                        ds_frame = DataFrame(data_source, index=[0])
        elif isinstance(data_source, DataFrame):
            ds_frame = data_source
        else:
            return CreateTableSourceFailure(
                error_kind=CreateTableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
                info=str(type(data_source)),
            )

        pre_fingerprint = config.previous_fingerprint
        cur_fingerprint = create_fingerprint(ds_frame, data_source)
        if pre_fingerprint is not None and pre_fingerprint != cur_fingerprint:
            return CreateTableSourceFailure(
                error_kind=CreateTableSourceErrorKind.INVALID_FINGERPRINT,
                info=cur_fingerprint,
            )

        filter_frame = None
        filter_eval_expr = config.filter_eval_expr
        if filter_eval_expr is not None and filter_eval_expr != "":
            try:
                if config.filter_eval_expr_provide_frame:
                    # add required data-frame to make it accessible for eval
                    # "_df" is the synthetic identifier which should resolve to the data-frame
                    caller_globals["_df"] = ds_frame
                filter_frame = eval(filter_eval_expr, caller_globals)
            except Exception as e:
                return CreateTableSourceFailure(
                    error_kind=CreateTableSourceErrorKind.FILTER_FRAME_EVAL_FAILED,
                    info=repr(e),
                )

            if not isinstance(filter_frame, DataFrame):
                return CreateTableSourceFailure(
                    error_kind=CreateTableSourceErrorKind.FILTER_FRAME_OF_WRONG_TYPE,
                    info=str(type(filter_frame)),
                )

        return TableSource(
            FrameContext(ds_frame, FilterCriteria.from_frame(filter_frame)),
            fingerprint=cur_fingerprint,
        )
