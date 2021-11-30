
from plugin_code.apply_fallback_patch import ApplyFallbackPatch
from plugin_code.apply_map_fallback_patch import ApplyMapFallbackPatch
from plugin_code.background_gradient_patch import BackgroundGradientPatch
from plugin_code.base_apply_map_patcher import BaseApplyMapPatcher
from plugin_code.base_apply_patcher import BaseApplyPatcher
from plugin_code.exported_style import ExportedStyle
from plugin_code.highlight_extrema_patch import HighlightExtremaPatch
from plugin_code.table_structure import TableStructure

# == copy after here ==
import inspect
import numpy as np
from pandas import DataFrame
from pandas.io.formats.style import Styler
from typing import Callable, List, Tuple, Union


class PatchedStyler:

    def __init__(self, styler: Styler):
        self.__styler = styler
        self.__visible_df = self.__get_visible_df(styler)
        self.__patched_styles = self.__patch_styles(styler.export())

    def render_chunk(
            self,
            first_row: int,
            first_column: int,
            last_row: int,
            last_column: int,
            exclude_row_header: bool = False,
            exclude_column_header: bool = False
    ) -> str:
        chunk: DataFrame = self.__visible_df.iloc[first_row:last_row, first_column:last_column]
        chunk_styler = chunk.style
        self.__apply_styler_configurations(self.__styler, chunk_styler)
        self.__prevent_unnecessary_html(chunk_styler)
        if exclude_row_header:
            chunk_styler.hide_index()
        for p in self.__patched_styles:
            p.apply_to_styler(chunk_styler)
        return self.__create_html(chunk_styler, first_row, first_column)

    def __create_html(self, chunk_styler, first_row: int, first_column: int) -> str:
        body = f'<body>{chunk_styler.render(encoding="utf-8", uuid="")}</body>'
        chunk_df = chunk_styler.data
        meta_ri = f'<meta name="row_indexer" content="{first_row}" />'
        if len(self.__styler.hidden_columns) == 0:
            meta_ci = f'<meta name="col_indexer" content="{first_column}" />'
        else:
            meta_ci = f'<meta name="col_indexer" content="{self.__styler.columns.get_indexer_for(chunk_df.columns)}" />'
        head = f"<head>{meta_ri}{meta_ci}</head>"
        return f'<html>{head}{body}</html>'

    def render_unpatched(self) -> str:
        # this method is only used in unit tests or to create test data for the plugin
        # therefore it is save to change potential configured values
        self.__prevent_unnecessary_html(self.__styler)
        return self.__styler.render(encoding="utf-8", uuid="")

    def __patch_styles(self, styles: List[Tuple[Callable, tuple, dict]]) -> List[Union[BaseApplyPatcher, BaseApplyMapPatcher]]:
        patched_styles = []
        frame = self.__styler.data
        for t in styles:

            exported_style = ExportedStyle(t)
            apply_func = exported_style.apply_func()
            apply_kwargs = exported_style.apply_kwargs()
            apply_args_func = exported_style.apply_args_func()

            if self.__is_builtin_style(apply_args_func):
                if self.__is_builtin_background_gradient(apply_args_func):
                    patched_styles.append(BackgroundGradientPatch(frame, exported_style.create_apply_args(), apply_kwargs))
                elif self.__is_builtin_highlight_extrema(apply_args_func):
                    patched_styles.append(HighlightExtremaPatch(frame, exported_style.create_apply_args(), apply_kwargs))
                elif self.__is_builtin_highlight_null(apply_args_func):
                    patched_styles.append(ApplyMapFallbackPatch(frame, exported_style.create_apply_map_args(), apply_kwargs))
                elif self.__is_builtin_set_properties(apply_args_func):
                    patched_styles.append(ApplyMapFallbackPatch(frame, exported_style.create_apply_map_args(), apply_kwargs))
                continue
            elif self.__is_builtin_applymap(apply_func):
                patched_styles.append(
                    ApplyMapFallbackPatch(frame, exported_style.create_apply_map_args(), apply_kwargs))
            else:
                patched_styles.append(ApplyFallbackPatch(frame, exported_style.create_apply_args(), apply_kwargs))

        return patched_styles

    def get_table_structure(self) -> TableStructure:
        return TableStructure(
            rows_count=len(self.__styler.data.index),
            columns_count=len(self.__styler.data.columns),
            visible_columns_count=len(self.__visible_df.columns),
            row_levels_count=self.__styler.data.index.nlevels,
            column_levels_count=self.__styler.data.columns.nlevels,
            hide_row_header=self.__styler.hidden_index
        )

    @staticmethod
    def __get_visible_df(styler: Styler) -> DataFrame:
        if len(styler.hidden_columns) == 0:
            return styler.data
        else:
            visible_columns = np.delete(styler.columns.get_indexer_for(styler.columns), styler.hidden_columns)
            return styler.data.iloc[:, visible_columns]

    @staticmethod
    def __prevent_unnecessary_html(styler: Styler):
        # https://pandas.pydata.org/pandas-docs/stable/user_guide/style.html#Optimization
        # to reduce size of generated html string
        # 1) use an empty uuid
        #
        # pandas 1.1 "styler.set_uuid("")" has no effect (bug?)
        # pass "uuid=''" to the render method to fix it
        styler.set_uuid("")
        # 2) disable general cellIds, only cells with a styling will have ids
        # note: Tooltips require cell_ids to work
        styler.cell_ids = False

    @staticmethod
    def __is_builtin_style(func: Callable) -> bool:
        return inspect.getmodule(func).__name__ == 'pandas.io.formats.style'

    @staticmethod
    def __is_builtin_background_gradient(func: Callable) -> bool:
        return func.__qualname__ == 'Styler._background_gradient'

    @staticmethod
    def __is_builtin_highlight_extrema(func: Callable) -> bool:
        return func.__qualname__.startswith('Styler._highlight_extrema')

    @staticmethod
    def __is_builtin_highlight_null(func: Callable) -> bool:
        return func.__qualname__.startswith('Styler._highlight_null')

    @staticmethod
    def __is_builtin_set_properties(func: Callable) -> bool:
        return func.__qualname__.startswith('Styler.set_properties')

    @staticmethod
    def __is_builtin_applymap(func: Callable) -> bool:
        return func.__qualname__.startswith('Styler.applymap')

    @staticmethod
    def __apply_styler_configurations(source_styler: Styler, chunk_styler: Styler):
        chunk_styler.hidden_index = source_styler.hidden_index
        # "hidden_columns" don't have to be copied because "__visible_df" is already used
        # which guarantees that only the visible columns are rendered

        has_display_funcs = source_styler._display_funcs is not None and len(source_styler._display_funcs) > 0

        if has_display_funcs:
            ri = source_styler.index.get_indexer_for(chunk_styler.index)
            ci = source_styler.columns.get_indexer_for(chunk_styler.columns)

            chunk_styler.format(None)  # init dict

            for c_index_in_chunk, c_index_in_source in enumerate(ci):
                for r_index_in_chunk, r_index_in_source in enumerate(ri):
                    target_key = (r_index_in_chunk, c_index_in_chunk)
                    source_key = (r_index_in_source, c_index_in_source)

                    if source_key in source_styler._display_funcs:
                        chunk_styler._display_funcs[target_key] = source_styler._display_funcs[source_key]
