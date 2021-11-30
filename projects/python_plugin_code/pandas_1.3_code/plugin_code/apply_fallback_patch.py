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
