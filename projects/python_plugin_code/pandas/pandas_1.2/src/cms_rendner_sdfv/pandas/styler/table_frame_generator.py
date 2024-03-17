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
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Callable, Dict, List, Optional, Set, Tuple

from pandas.io.formats.style import Styler

from cms_rendner_sdfv.base.table_source import AbstractTableFrameGenerator
from cms_rendner_sdfv.base.types import Region, TableFrame, TableFrameCell, TableFrameColumn, TableFrameLegend
from cms_rendner_sdfv.pandas.shared.value_formatter import ValueFormatter
from cms_rendner_sdfv.pandas.shared.visible_frame import Chunk
from cms_rendner_sdfv.pandas.styler.patched_styler_context import PatchedStylerContext
from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo


@dataclass
class _CSSPropsWithIndex:
    props: Dict[str, str]
    index: int


@dataclass
class _SpannedElement:
    row_span: int
    col_span: int
    element: dict


class _TranslateKeysDict(Mapping, dict):

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


class TableFrameGenerator(AbstractTableFrameGenerator):
    def __init__(self,
                 styler_context: PatchedStylerContext,
                 todos_filter: Optional[Callable[[StylerTodo], bool]] = None,
                 ):
        super().__init__(styler_context.visible_frame)
        self.__styler_context: PatchedStylerContext = styler_context
        self.__todos_filter: Optional[Callable[[StylerTodo], bool]] = todos_filter

    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        # -- Compute styling
        # The plugin only renders the visible (non-hidden cols/rows) of the styler DataFrame
        # therefore the chunk is created from the visible data.
        chunk = self.__styler_context.visible_frame.get_chunk(region)
        chunk_df = chunk.to_frame()

        # The apply/map params are patched to not operate outside the chunk bounds.
        chunk_aware_todos = self.__styler_context.create_patched_todos(chunk_df, self.__todos_filter)

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
        chunk_styler = chunk_df.style
        self.__copy_styler_state(source=computed_styler, target=chunk_styler)

        # The styler of the original DataFrame can contain additional configuration for styling the rendered html table.
        # For example display functions transform DataFrame values into a string which should be rendered
        # instead of the value.
        # To use these additional configurations, an index mapping is used to translate a chunk row/col index into a
        # row/col index of the original DataFrame.
        translate_key = chunk.get_translate_into_source_frame_cell_coordinates()
        rit = lambda r: translate_key((r, 0))[0]
        cit = lambda c: translate_key((0, c))[1]

        chunk_styler.ctx = _TranslateKeysDict(computed_styler.ctx, translate_key)
        self.__copy_over_cell_context(source=computed_styler, target=chunk_styler, ri_translator=rit, ci_translator=cit)
        chunk_styler._display_funcs = _TranslateKeysDict(computed_styler._display_funcs, translate_key)

        html_props = chunk_styler._translate()

        return self._convert_to_table_frame(
            html_props,
            chunk,
            exclude_row_header=exclude_row_header,
            exclude_col_header=exclude_col_header,
            formatter=ValueFormatter(),
        )

    @staticmethod
    def __copy_over_cell_context(
            source: Styler,
            target: Styler,
            ri_translator: Callable[[int], int],
            ci_translator: Callable[[int], int],
    ):
        if source.cell_context is not None and "data" in source.cell_context:

            source_data = source.cell_context["data"]
            target_data = {}

            def translate_column_key(k):
                return ci_translator(k)

            for source_row_key, source_row in source_data.items():
                if len(source_row) > 0:
                    target_data[source_row_key] = _TranslateKeysDict(source_row, translate_column_key)

            target.cell_context = {"data": _TranslateKeysDict(target_data, lambda k: ri_translator(k))}

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
        #   - already used to calculate "self.__styler_context.visible_frame"
        #     and therefore not needed any more
        # "ctx"
        #   - gets modified/filled when generating html
        #   - causes html output with wrong values when multiple targets copy the
        #     same ref (source) and are processed in different threads => each thread
        #     modifies the same ref (ctx is cleared and new values are added)

    def _convert_to_table_frame(self,
                                html_props: dict,
                                chunk: Chunk,
                                exclude_row_header: bool,
                                exclude_col_header: bool,
                                formatter: ValueFormatter,
                                ) -> TableFrame:
        # html_props => {uuid, table_styles, caption, head, body, cellstyle, table_attributes}

        self._resolve_spans(html_props, "head")
        self._resolve_spans(html_props, "body")

        columns = [] if exclude_col_header else self._extract_columns(html_props, chunk, formatter)
        index_labels = [] if exclude_row_header else self._extract_index_header_labels(html_props, formatter)
        cells = self._extract_cells(html_props, formatter)
        legend_label = None if exclude_col_header and exclude_row_header else self._extract_legend_label(html_props, formatter)

        return TableFrame(
            index_labels=index_labels,
            columns=columns,
            legend=legend_label,
            cells=cells,
        )

    def _resolve_spans(self, html_props: dict, rows_key: str):
        open_spans: Dict[int, List[_SpannedElement]] = {}

        rows = html_props.get(rows_key, [])
        for ri, row in enumerate(rows):
            updated_row = []
            for ci, element in enumerate(row):
                element_to_add = element
                spanned_element = self.__create_spanned_element_from_span_attributes(element)
                if spanned_element is not None:
                    element_to_add = None
                    open_spans.setdefault(ci, []).append(spanned_element)

                if ci in open_spans:
                    pending_col_spans = open_spans[ci]
                    remove_consumed_spans = False
                    for pending_span in pending_col_spans:
                        updated_row.extend(pending_span.col_span * [pending_span.element])
                        pending_span.row_span -= 1
                        if pending_span.row_span < 1:
                            remove_consumed_spans = True

                    if remove_consumed_spans:
                        cleaned = [s for s in pending_col_spans if s.row_span > 0]
                        if len(cleaned) == 0:
                            del open_spans[ci]
                        else:
                            open_spans[ci] = cleaned

                if element_to_add is not None:
                    updated_row.append(element_to_add)

            rows[ri] = updated_row

    def __create_spanned_element_from_span_attributes(self, element: dict) -> Optional[_SpannedElement]:
        attributes = self.__extract_attributes(element)
        if attributes is None:
            return None

        rowspan = attributes.get("rowspan", None)
        colspan = attributes.get("colspan", None)
        if rowspan is None and colspan is None:
            return None

        element["attributes"] = ''

        rowspan = 1 if rowspan is None else int(rowspan)
        colspan = 1 if colspan is None else int(colspan)
        if rowspan > 1 or colspan > 1:
            return _SpannedElement(rowspan, colspan, element)

        return None

    @staticmethod
    def __extract_attributes(element: dict) -> Optional[Dict[str, str]]:
        attributes = element.get("attributes", '')  # empty attributes can be defined as an empty string instead of list
        if not attributes or attributes == '':
            return None
        if isinstance(attributes, list):
            attributes_dict = {}
            for attr in attributes:
                key, value = attr.split("=")
                # note: most attributes are formatted like: 'rowspan="2"' (extra quotes around the value)
                # but some pandas versions also formatted attributes like: 'colspan=2' (no extra quotes)
                # -> instead of "value[1:-1]" use "value.strip('"')"
                attributes_dict[key] = value.strip('"')
            return attributes_dict
        return None

    @staticmethod
    def _extract_legend_label(html_props: dict, formatter: ValueFormatter) -> TableFrameLegend:
        index_legend = []
        column_legend = []

        head = html_props.get("head", [])
        if head:
            last_row = head[-1]

            # last row contains only the index-legend if there is a leveled-index with level names
            for element in last_row:
                element_classes = set(element.get("class", "").split(" "))
                if element.get("is_visible", True):
                    if "index_name" in element_classes:
                        display_value = element.get("display_value", element.get("value", ""))
                        index_legend.append(formatter.format_index(display_value))
                if "col_heading" in element_classes:
                    # found a column label, row doesn't contain the index-legend
                    index_legend = []
                    break

            other_rows = head if not index_legend else head[:-1]
            for row in other_rows:

                for element in row:
                    if element.get("is_visible", True):
                        element_classes = set(element.get("class", "").split(" "))
                        is_index_name = "index_name" in element_classes

                        if is_index_name:
                            display_value = element.get("display_value", element.get("value", ""))
                            column_legend.append(formatter.format_index(display_value))
                            # there should be only one header per row which belongs to the column-legend
                            break

        return TableFrameLegend(index=index_legend, column=column_legend) if index_legend or column_legend else None

    def _extract_columns(self, html_props: dict, chunk: Chunk, formatter: ValueFormatter) -> List[TableFrameColumn]:
        result: List[TableFrameColumn] = []

        # leveled column names span multiple rows (one level per row)
        for row in html_props.get("head", []):

            is_first_row = not result
            col_heading_index = 0

            for element in row:
                if element.get("is_visible", True):
                    element_classes = set(element.get("class", "").split(" "))
                    is_column_header = "col_heading" in element_classes

                    if is_column_header:
                        display_value = element.get("display_value", "")
                        if is_first_row:
                            result.append(
                                TableFrameColumn(
                                    dtype=str(chunk.dtype_at(col_heading_index)),
                                    labels=[formatter.format_column(display_value)],
                                    describe=None if self._exclude_column_describe else chunk.describe_at(
                                        col_heading_index),
                                )
                            )
                        else:
                            result[col_heading_index].labels.append(display_value)
                        col_heading_index += 1

        return result

    @staticmethod
    def _extract_index_header_labels(html_props: dict, formatter: ValueFormatter) -> List[List[str]]:
        result: List[List[str]] = []

        for row in html_props.get("body", []):

            index_label = []

            for element in row:
                if element.get("type", "") == "td":
                    break
                if element.get("is_visible", True):
                    element_classes = set(element.get("class", "").split(" "))
                    is_index_header = "row_heading" in element_classes

                    if is_index_header:
                        display_value = element.get("display_value", "")
                        index_label.append(formatter.format_index(display_value))

            if index_label:
                result.append(index_label)

        return result

    def _extract_cells(self, html_props: dict, formatter: ValueFormatter) -> List[List[TableFrameCell]]:
        result: List[List[TableFrameCell]] = []

        css_dict = self.__create_css_dict(html_props)

        for row in html_props.get("body", []):

            cells_in_row = []

            for element in row:

                if element.get("type", "") == "td" and element.get("is_visible", True):
                    element_classes = set(element.get("class", "").split(" "))

                    if "data" in element_classes:
                        cells_in_row.append(
                            TableFrameCell(
                                value=formatter.format_cell(element.get("display_value", "")),
                                css=self._get_css_dict(element.get("id", None), element_classes, css_dict),
                            ),
                        )

            if cells_in_row:
                result.append(cells_in_row)

        return result

    @staticmethod
    def _get_css_dict(element_id: str, element_classes: Set[str], css_dict: Dict[str, _CSSPropsWithIndex]) -> \
            Optional[dict]:
        if not css_dict:
            return None

        matching_css_props: List[_CSSPropsWithIndex] = []

        for c in element_classes:
            css_props = css_dict.get(c, None)
            if css_props is not None:
                matching_css_props.append(css_props)

        if matching_css_props:
            # sort highest index last
            # note: selector-specificity is not taken into account
            # https://www.w3.org/TR/selectors-3/#specificity
            matching_css_props.sort(key=lambda x: x.index)

        id_css_props = css_dict.get(element_id, None)
        if id_css_props is not None:
            # props for id selector have higher priority over class selectors
            # add them last
            matching_css_props.append(id_css_props)

        if not matching_css_props:
            return None

        result: Dict[str, str] = {}
        for css_props in matching_css_props:
            # keep it simple for now - don't handle "!important" and other stuff
            result.update(css_props.props)

        return result

    @staticmethod
    def __create_css_dict(html_props: dict) -> Dict[str, _CSSPropsWithIndex]:
        cellstyle = html_props.get("cellstyle", None)
        css_dict: Dict[str, _CSSPropsWithIndex] = {}
        if cellstyle is not None:
            for index, entry in enumerate(cellstyle):
                props = entry['props']
                if not props:
                    continue
                # note: remove extra space in front of the values (formatting bug in pandas 1.2)
                css_props = _CSSPropsWithIndex({p[0]: p[1].lstrip(' ') for p in props}, index)
                for s in entry.get('selectors', []):
                    css_dict[s] = css_props
        return css_dict
