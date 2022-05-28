#  Copyright 2022 cms.rendner (Daniel Schmidt)
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
from plugin_code.table_structure import TableStructure
from plugin_code.todos_patcher import TodosPatcher

# == copy after here ==
import numpy as np
from pandas import DataFrame
from pandas.io.formats.style import Styler
from typing import List, Tuple, Callable
from collections.abc import Mapping
from abc import abstractmethod, ABC


class _IndexTranslator(ABC):
    @abstractmethod
    def translate(self, index):
        pass


class _SequenceIndexTranslator(_IndexTranslator):
    def __init__(self, seq):
        super().__init__()
        self.__seq = seq

    def translate(self, index):
        return self.__seq[index]


class _OffsetIndexTranslator(_IndexTranslator):
    def __init__(self, offset: int):
        super().__init__()
        self.__offset = offset

    def translate(self, index):
        return index + self.__offset


class _TranslateKeysDict(Mapping):

    def __init__(self, org_dict: dict, translate_key: Callable):
        self._org_dict = org_dict
        self._translate_key = translate_key

    def get(self, key, default=None):
        t_key = self._translate_key(key)
        if t_key not in self._org_dict:
            return default
        return self._org_dict.get(t_key)

    def __contains__(self, key):
        return self._translate_key(key) in self._org_dict

    def __getitem__(self, key):
        return self._org_dict[self._translate_key(key)]

    def values(self):
        return super().values()

    def __iter__(self):
        raise NotImplementedError

    def keys(self):
        raise NotImplementedError

    def items(self):
        raise NotImplementedError

    def __len__(self):
        return len(self._org_dict)


class _CSSRowColIndexAdjuster:

    def __init__(self, ri_translator: _IndexTranslator, ci_translator: _IndexTranslator):
        self.ri_translator = ri_translator
        self.ci_translator = ci_translator

    def adjust(self, d: dict):
        # d => {uuid, table_styles, caption, head, body, cellstyle, table_attributes}
        for head in d.get('head', []):
            for col in head:
                if 'id' in col:
                    col['id'] = '_'.join(self._adjust_indices(col['id'].split('_')))
                if 'class' in col:
                    col['class'] = ' '.join(self._adjust_indices(col['class'].strip().split(' ')))

        for row in d.get('body', []):
            for entry in row:
                if 'id' in entry:
                    entry['id'] = '_'.join(self._adjust_indices(entry['id'].split('_')))
                if 'class' in entry:
                    entry['class'] = ' '.join(self._adjust_indices(entry['class'].strip().split(' ')))

        for style in d.get('cellstyle', []):
            if 'selectors' in style:
                style['selectors'] = ['_'.join(self._adjust_indices(s.split('_'))) for s in style['selectors']]

    def _adjust_indices(self, indices: List[str]) -> List[str]:
        return [self._adjust_index(x) for x in indices]

    def _adjust_index(self, index: str) -> str:
        if index.startswith("row") and index[3:].isdigit():
            return f'row{self.ri_translator.translate(int(index[3:]))}'
        if index.startswith("col") and index[3:].isdigit():
            return f'col{self.ci_translator.translate(int(index[3:]))}'
        return index


class PatchedStyler:

    def __init__(self, styler: Styler):
        self.__styler: Styler = styler
        self.__visible_data: DataFrame = self.__get_visible_data(styler)

    def render_chunk(
            self,
            first_row: int,
            first_column: int,
            last_row: int,
            last_column: int,
            exclude_row_header: bool = False,
            exclude_column_header: bool = False
    ) -> str:
        # chunk contains always only non-hidden data
        chunk = self.__visible_data.iloc[first_row:last_row, first_column:last_column]

        patched_todos = TodosPatcher().patch_todos_for_chunk(self.__styler, chunk)

        computed_styler = self.__compute_styles(
            patched_todos=patched_todos,
            exclude_row_header=exclude_row_header,
            exclude_column_header=exclude_column_header,
        )
        html_props = self.__generate_html_props_for_chunk(
            chunk=chunk,
            first_row=first_row,
            first_column=first_column,
            computed_styler=computed_styler,
        )

        # use templates of original styler
        return self.__styler.template_html.render(
            **html_props,
            encoding="utf-8",
            sparse_columns=False,
            sparse_index=False,
            doctype_html=True,
            html_table_tpl=self.__styler.template_html_table,
            html_style_tpl=self.__styler.template_html_style,
        )

    def __compute_styles(self,
                         patched_todos: List[Tuple[Callable, tuple, dict]],
                         exclude_row_header: bool = False,
                         exclude_column_header: bool = False,
                         ) -> Styler:
        # create a copy to not pollute original styler
        copy = self.__styler.data.style
        self.__copy_styler_state(source=self.__styler, target=copy)

        # assign todos
        copy._todo = patched_todos

        # only hide if forced
        if exclude_row_header:
            copy.hide_index()
        if exclude_column_header:
            copy.hide_columns()

        # operate on copy
        copy._compute()
        return copy

    def __generate_html_props_for_chunk(self,
                                        chunk: DataFrame,
                                        first_row: int,
                                        first_column: int,
                                        computed_styler: Styler,
                                        ):
        if len(self.__styler.hidden_rows) == 0:
            rit = _OffsetIndexTranslator(first_row)
        else:
            rit = _SequenceIndexTranslator(self.__styler.index.get_indexer_for(chunk.index))

        if len(self.__styler.hidden_columns) == 0:
            cit = _OffsetIndexTranslator(first_column)
        else:
            cit = _SequenceIndexTranslator(self.__styler.columns.get_indexer_for(chunk.columns))

        # prepare chunk styler
        chunk_styler = chunk.style
        self.__copy_styler_state(source=computed_styler, target=chunk_styler)

        # translate keys from "chunk_styler" into keys of "computed_styler"
        def translate_key(k):
            return rit.translate(k[0]), cit.translate(k[1])

        chunk_styler.ctx = _TranslateKeysDict(computed_styler.ctx, translate_key)
        chunk_styler.cell_context = _TranslateKeysDict(computed_styler.cell_context, translate_key)
        chunk_styler._display_funcs = _TranslateKeysDict(computed_styler._display_funcs, translate_key)

        # generate html props for chunk
        result = chunk_styler._translate(sparse_index=False, sparse_cols=False)

        # translated props doesn't know about the chunk
        # therefore some row/col indices have to be adjusted
        # to have the correct index
        _CSSRowColIndexAdjuster(rit, cit).adjust(result)

        return result

    def render_unpatched(self) -> str:
        # this method is only used in unit tests or to create test data for the plugin
        # therefore it is save to change potential configured values
        self.__styler.uuid = ''
        self.__styler.uuid_len = 0
        self.__styler.cell_ids = False
        return self.__styler.render(
            encoding="utf-8",
            doctype_html=True,
            sparse_columns=False,
            sparse_index=False,
        )

    def get_table_structure(self) -> TableStructure:
        return TableStructure(
            rows_count=len(self.__visible_data.index),
            columns_count=len(self.__visible_data.columns),
            row_levels_count=self.__visible_data.index.nlevels,
            column_levels_count=self.__visible_data.columns.nlevels,
            hide_row_header=self.__styler.hide_index_,
            hide_column_header=self.__styler.hide_columns_
        )

    @staticmethod
    def __get_visible_data(styler: Styler) -> DataFrame:
        if len(styler.hidden_rows) == 0 and len(styler.hidden_columns) == 0:
            return styler.data
        else:
            visible_indices = np.delete(styler.index.get_indexer_for(styler.index), styler.hidden_rows)
            visible_columns = np.delete(styler.columns.get_indexer_for(styler.columns), styler.hidden_columns)
            return styler.data.iloc[visible_indices, visible_columns]

    @staticmethod
    def __copy_styler_state(
            source: Styler,
            target: Styler,
    ):
        # clear
        target.uuid = ''
        target.uuid_len = 0
        target.cell_ids = False

        # copy/assign
        target.table_styles = source.table_styles
        target.table_attributes = source.table_attributes
        target.hide_columns_ = source.hide_columns_
        target.hide_index_ = source.hide_index_
        target.cell_context = source.cell_context
        target._display_funcs = source._display_funcs
        # don't copy/assign:
        # "_todo"
        #   - will be overwritten with the patched ones in a later step
        # "hidden_columns" and "self.hidden_rows"
        #   - these values are already used to calculate "self.__visible_data"
        #     and therefore not needed any more
        # "ctx"
        #   - gets modified/filled when generating html
        #   - causes html output with wrong values when multiple targets copy the
        #     same ref (source) and are processed in different threads => each thread
        #     modifies the same ref (ctx is cleared and new values are added)
