#  Copyright 2023 cms.rendner (Daniel Schmidt)
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
from plugin_code.custom_json_encoder import CustomJSONEncoder
from plugin_code.create_fingerprint import create_fingerprint
from plugin_code.patched_styler import PatchedStyler
from plugin_code.patched_styler_context import PatchedStylerContext, FilterCriteria

# == copy after here ==
import json
import inspect
from dataclasses import dataclass
from typing import Union, Optional
from pandas import DataFrame
from pandas.io.formats.style import Styler


@dataclass(frozen=True)
class CreatePatchedStylerConfig:
    data_source_to_frame_hint: Optional[str] = None
    previous_fingerprint: Optional[str] = None
    filter_eval_expr: Optional[str] = None
    filter_eval_expr_provide_frame: Optional[bool] = None


@dataclass(frozen=True)
class CreatePatchedStylerFailure:
    error_kind: str
    info: str

    def to_json(self) -> str:
        return json.dumps(self, cls=CustomJSONEncoder)


class StyledDataFrameViewerBridge:
    @staticmethod
    def create_patched_styler(
            data_source: Union[DataFrame, Styler, dict],
            create_config: CreatePatchedStylerConfig = None,
    ) -> Union[PatchedStyler, str]:
        config = create_config if create_config is not None else CreatePatchedStylerConfig()
        ds_frame = None
        ds_frame_style = None
        if isinstance(data_source, dict):
            if config.data_source_to_frame_hint == "DictKeysAsRows":
                ds_frame = DataFrame.from_dict(data_source, orient='index')
            elif all(name in data_source for name in ["index", "columns", "data", "index_names", "column_names"]):
                ds_frame = DataFrame.from_dict(data_source, orient='tight')
            else:
                ds_frame = DataFrame.from_dict(data_source, orient='columns')
            ds_frame_style = ds_frame.style
        elif isinstance(data_source, DataFrame):
            ds_frame = data_source
            ds_frame_style = data_source.style
        elif isinstance(data_source, Styler):
            ds_frame = data_source.data
            ds_frame_style = data_source
        else:
            return CreatePatchedStylerFailure(error_kind="UNSUPPORTED_DATA_SOURCE_TYPE",
                                              info=type(data_source)).to_json()

        pre_fingerprint = config.previous_fingerprint
        cur_fingerprint = create_fingerprint(ds_frame, data_source)
        if pre_fingerprint is not None and pre_fingerprint != cur_fingerprint:
            return CreatePatchedStylerFailure(error_kind="INVALID_FINGERPRINT", info=cur_fingerprint).to_json()

        filter_frame = None
        filter_eval_expr = config.filter_eval_expr
        if filter_eval_expr is not None and filter_eval_expr != "":
            try:
                # use globals and locals of previous frame (caller frame)
                parent_frame = inspect.currentframe().f_back
                updated_locals = {**parent_frame.f_locals}
                if config.filter_eval_expr_provide_frame:
                    # add required data-frame to make it accessible for eval
                    # "_df" is the synthetic identifier which should resolve to the data-frame
                    updated_locals["_df"] = ds_frame
                filter_frame = eval(filter_eval_expr, parent_frame.f_globals, updated_locals)
            except Exception as e:
                return CreatePatchedStylerFailure(error_kind="FILTER_FRAME_EVAL_FAILED", info=repr(e)).to_json()

            if not isinstance(filter_frame, DataFrame):
                return CreatePatchedStylerFailure(error_kind="FILTER_FRAME_OF_WRONG_TYPE",
                                                  info=type(filter_frame)).to_json()

        return PatchedStyler(
            PatchedStylerContext(ds_frame_style, FilterCriteria.from_frame(filter_frame)),
            fingerprint=cur_fingerprint,
        )
