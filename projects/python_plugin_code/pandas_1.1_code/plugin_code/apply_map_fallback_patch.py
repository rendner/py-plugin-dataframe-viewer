from plugin_code.apply_map_args import ApplyMapArgs
from plugin_code.base_apply_map_patcher import BaseApplyMapPatcher

# == copy after here ==
from pandas import DataFrame


class ApplyMapFallbackPatch(BaseApplyMapPatcher):

    def __init__(self, data: DataFrame, apply_args: ApplyMapArgs, func_kwargs: dict):
        BaseApplyMapPatcher.__init__(self, data, apply_args, func_kwargs)

    def _exec_patched_func(self, scalar):
        return self._apply_args.func()(scalar, **self._func_kwargs)
