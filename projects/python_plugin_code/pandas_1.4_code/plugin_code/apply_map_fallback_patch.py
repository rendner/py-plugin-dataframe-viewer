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
from plugin_code.apply_map_args import ApplyMapArgs
from plugin_code.base_apply_map_patcher import BaseApplyMapPatcher

# == copy after here ==
from pandas import DataFrame


class ApplyMapFallbackPatch(BaseApplyMapPatcher):

    def __init__(self, data: DataFrame, apply_args: ApplyMapArgs, func_kwargs: dict):
        BaseApplyMapPatcher.__init__(self, data, apply_args, func_kwargs)

    def _exec_patched_func(self, scalar):
        return self._apply_args.func()(scalar, **self._func_kwargs)
