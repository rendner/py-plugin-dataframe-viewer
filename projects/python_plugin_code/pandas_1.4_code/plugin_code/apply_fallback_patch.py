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
from plugin_code.apply_args import ApplyArgs
from plugin_code.base_apply_patcher import BaseApplyPatcher

# == copy after here ==
from pandas import DataFrame
import inspect


class ApplyFallbackPatch(BaseApplyPatcher):

    def __init__(self, data: DataFrame, apply_args: ApplyArgs, func_kwargs: dict):
        BaseApplyPatcher.__init__(self, data, apply_args, func_kwargs)
        self.__provide_chunk_parent = self._should_provide_chunk_parent()

    def _exec_patched_func(self, chunk: DataFrame):
        if self.__provide_chunk_parent:
            return self._apply_args.func()(chunk, **dict(self._func_kwargs, chunk_parent=self._get_parent(chunk)))
        else:
            return self._apply_args.func()(chunk, **self._func_kwargs)

    def _should_provide_chunk_parent(self):
        sig = inspect.signature(self._apply_args.func())
        for param in sig.parameters.values():
            if param.name == "chunk_parent" or param.kind == inspect.Parameter.VAR_KEYWORD:
                return True
        return False
