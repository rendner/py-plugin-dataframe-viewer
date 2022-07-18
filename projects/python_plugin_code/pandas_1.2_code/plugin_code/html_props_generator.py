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
from plugin_code.patched_styler_context import PatchedStylerContext, Region, IndexTranslator

# == copy after here ==
from typing import List, Callable, Tuple
from collections.abc import Mapping
from pandas.io.formats.style import Styler


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


class _HTMLPropsIndexAdjuster:

    def __init__(self, ri_translator: IndexTranslator, ci_translator: IndexTranslator):
        self.ri_translator = ri_translator
        self.ci_translator = ci_translator

    def adjust(self, html_props: dict):
        for head in html_props.get('head', []):
            for entry in head:
                self.adjust_id_and_class(entry)

        for row in html_props.get('body', []):
            for entry in row:
                self.adjust_id_and_class(entry)

        for style in html_props.get('cellstyle', []):
            if 'selectors' in style:
                style['selectors'] = ['_'.join(self._adjust_row_col_indices(s.split('_'))) for s in style['selectors']]

    def adjust_id_and_class(self, entry: dict):
        if 'id' in entry:
            entry['id'] = '_'.join(self._adjust_row_col_indices(entry['id'].split('_')))
        if 'class' in entry:
            entry['class'] = ' '.join(self._adjust_row_col_indices(entry['class'].strip().split(' ')))

    def _adjust_row_col_indices(self, indices: List[str]) -> List[str]:
        return [self._adjust_row_col_index(x) for x in indices]

    def _adjust_row_col_index(self, index: str) -> str:
        if index.startswith("row") and index[3:].isdigit():
            return f'row{self.ri_translator.translate(int(index[3:]))}'
        if index.startswith("col") and index[3:].isdigit():
            return f'col{self.ci_translator.translate(int(index[3:]))}'
        return index


class HTMLPropsGenerator:
    def __init__(self, styler_context: PatchedStylerContext):
        self.__styler_context: PatchedStylerContext = styler_context

    def compute_unpatched_props(self) -> dict:
        # can't use "styler._copy(deepcopy=True)" - pandas 1.2 only copies ctx and _todo)
        # therefore use org instead
        copy = self.__styler_context.get_styler()
        copy.uuid = ''
        copy.uuid_len = 0
        copy.cell_ids = False

        # bug in pandas 1.2.x - "_compute" doesn't clear the "ctx" before executing the style functions
        # each "_compute" call adds the same values again into the "ctx" (is fixed in pandas 1.3)
        # -> css styles are added multiple times, which breaks the "HtmlPropsValidator"
        copy.ctx.clear()

        copy._compute()

        return copy._translate()

    def compute_chunk_props(self,
                            region: Region,
                            exclude_row_header: bool = False,
                            translate_indices: bool = True,
                            ) -> dict:
        # -- Computing of styling
        # The plugin only renders the visible (non-hidden cols/rows) of the styled DataFrame
        # therefore the chunk is created from the visible data.
        chunk = self.__styler_context.get_visible_frame().iloc[
                region.first_row: region.first_row + region.rows,
                region.first_col: region.first_col + region.cols,
                ]

        # The apply/applymap params are patched to not operate outside the chunk bounds.
        chunk_aware_todos = self.__styler_context.create_patched_todos(chunk)

        # Compute the styling for the chunk by operating on the original DataFrame.
        # The computed styler contains only entries for the cells of the chunk,
        # this is ensured by the patched todos.
        computed_styler = self.__compute_styling(
            chunk_aware_todos=chunk_aware_todos,
            exclude_row_header=exclude_row_header,
        )

        # -- Generating html-props
        # pandas generates html-props into a dict for template rendering, this is done by iterating through
        # the whole DataFrame of a styler.
        #
        # The styling was computed on the original DataFrame but only for the cells of the chunk. To generate only
        # the html-props of the chunk, a styler which refers to the chunk DataFrame has to be created with the
        # already computed styling.
        chunk_styler = chunk.style
        self.__copy_styler_state(source=computed_styler, target=chunk_styler)

        # The styler of the original DataFrame can contain additional configuration for styling the rendered html table.
        # For example display functions allow to transform DataFrame values into a string which should be rendered
        # instead of the value.
        # To use these additional configurations, an index mapping is used to translate an chunk row/col index into a
        # row/col index of the original DataFrame.
        rit = self.__styler_context.get_row_index_translator_for_chunk(chunk, region)
        cit = self.__styler_context.get_column_index_translator_for_chunk(chunk, region)

        # translate keys from "chunk_styler" into keys of "computed_styler"
        def translate_key(k):
            return rit.translate(k[0]), cit.translate(k[1])

        chunk_styler.ctx = _TranslateKeysDict(computed_styler.ctx, translate_key)
        chunk_styler._display_funcs = _TranslateKeysDict(computed_styler._display_funcs, translate_key)
        self.__copy_over_cell_context(source=computed_styler, target=chunk_styler, ri_translator=rit, ci_translator=cit)

        # generate html-props for chunk
        result = chunk_styler._translate()
        # filter out empty styles, every cell will have a class
        # but the list of props may just be [['', '']].
        # "nested any" used to detect this
        trimmed = [x for x in result["cellstyle"] if any(any(y) for y in x["props"])]
        result["cellstyle"] = trimmed

        if translate_indices:
            # Translate the row/col indices, ids and css-selectors of the chunk.
            # Only required if the html-props of multiple chunks should be combined in a later step.
            # Chunks can contain elements with the same ids and css-selectors but different css-styling.
            _HTMLPropsIndexAdjuster(rit, cit).adjust(result)

        return result

    @staticmethod
    def __copy_over_cell_context(
            source: Styler,
            target: Styler,
            ri_translator: IndexTranslator,
            ci_translator: IndexTranslator,
    ):
        if source.cell_context is not None and "data" in source.cell_context:

            source_data = source.cell_context["data"]
            target_data = {}

            def translate_column_key(k):
                return ci_translator.translate(k)

            for source_row_key, source_row in source_data.items():
                if len(source_row) > 0:
                    target_data[source_row_key] = _TranslateKeysDict(source_row, translate_column_key)

            target.cell_context = {"data": _TranslateKeysDict(target_data, lambda k: ri_translator.translate(k))}

    def __compute_styling(self,
                          chunk_aware_todos: List[Tuple[Callable, tuple, dict]],
                          exclude_row_header: bool = False,
                          ) -> Styler:
        styler = self.__styler_context.get_styler()

        # create a new styler which refers to the same DataFrame to not pollute original styler
        copy = styler.data.style
        # copy required properties (not all properties should be copied)
        self.__copy_styler_state(source=styler, target=copy)

        # assign patched todos
        copy._todo = chunk_aware_todos

        # only hide if forced
        if exclude_row_header:
            copy.hide_index()

        # operate on copy
        copy._compute()
        return copy

    @staticmethod
    def __copy_styler_state(source: Styler, target: Styler):
        # clear
        target.uuid = ''
        target.uuid_len = 0
        target.cell_ids = False

        # copy/assign
        target.table_styles = source.table_styles
        target.table_attributes = source.table_attributes
        target.hidden_index = source.hidden_index
        target.cell_context = source.cell_context
        target._display_funcs = source._display_funcs
        # don't copy/assign:
        # "_todo"
        #   - will be overwritten with the patched ones in a later step
        # "hidden_columns"
        #   - already used to calculate "self.__styler_context.get_visible_frame()"
        #     and therefore not needed any more
        # "ctx"
        #   - gets modified/filled when generating html
        #   - causes html output with wrong values when multiple targets copy the
        #     same ref (source) and are processed in different threads => each thread
        #     modifies the same ref (ctx is cleared and new values are added)
