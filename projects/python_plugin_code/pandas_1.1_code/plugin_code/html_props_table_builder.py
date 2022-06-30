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

# == copy after here ==
from dataclasses import dataclass
from typing import List, Dict, Set, Any, Optional


@dataclass
class HTMLPropsTableElementCSS:
    id_props: Dict[str, str]
    class_props: List[Dict[str, str]]


@dataclass
class HTMLPropsTableRowElement:
    type: str
    display_value: str
    kind: str
    css_props: Optional[Dict[str, str]]
    attributes: Optional[Dict[str, Any]]


@dataclass
class HTMLPropsTable:
    body: List[List[HTMLPropsTableRowElement]]
    head: List[List[HTMLPropsTableRowElement]]


@dataclass
class _CSSPropsWithIndex:
    props: Dict[str, str]
    index: int


@dataclass
class _SpannedElement:
    row_span: int
    col_span: int
    element: HTMLPropsTableRowElement


class HTMLPropsTableBuilder:
    def __init__(self):
        self.__table = HTMLPropsTable([], [])

    def build_table(self) -> HTMLPropsTable:
        table = self.__table
        self.__table = HTMLPropsTable([], [])
        return table

    def append_props(self,
                     html_props: dict,
                     target_row_offset: int,
                     is_part_of_first_rows_in_chunk: bool,
                     is_part_of_first_cols_in_chunk: bool,
                     ):
        css_dict = self.__create_css_dict_from_cellstyles(html_props)

        if is_part_of_first_rows_in_chunk:
            self.__append_head_elements(html_props.get("head", []), css_dict)

        self.__append_body_elements(
            html_props.get("body", []),
            target_row_offset,
            is_part_of_first_cols_in_chunk,
            css_dict,
        )

    def __append_head_elements(self, head_elements: list, css_dict: Dict[str, _CSSPropsWithIndex]):
        transformed_head_elements = self.__transform_rows(head_elements, css_dict)
        if len(self.__table.head) == 0:
            self.__table.head.extend(transformed_head_elements)
        else:
            # continue rows
            for i, row in enumerate(transformed_head_elements):
                for element in row:
                    if element.kind == "col_heading":
                        self.__table.head[i].append(element)

    def __append_body_elements(self,
                               body_elements: list,
                               target_row_offset: int,
                               is_part_of_first_cols_in_chunk: bool,
                               css_dict: Dict[str, _CSSPropsWithIndex],
                               ):
        transformed_body_elements = self.__transform_rows(body_elements, css_dict)
        if is_part_of_first_cols_in_chunk:
            self.__table.body.extend(transformed_body_elements)
        else:
            # continue rows
            for i, row in enumerate(transformed_body_elements):
                target_row = self.__table.body[target_row_offset + i]
                for j, entry in enumerate(row):
                    if entry.type == 'td':
                        target_row.extend(row[j:])
                        break

    @staticmethod
    def __create_css_dict_from_cellstyles(html_props: dict) -> Dict[str, _CSSPropsWithIndex]:
        cellstyle = html_props.get("cellstyle", None)
        css_dict: Dict[str, _CSSPropsWithIndex] = {}
        if cellstyle is not None:
            for index, entry in enumerate(cellstyle):
                props = entry['props']
                if len(props) == 0:
                    continue
                css_props = _CSSPropsWithIndex({p[0]: p[1] for p in props}, index)
                for s in entry.get('selectors', []):
                    css_dict[s] = css_props
        return css_dict

    def __transform_rows(self,
                         rows: List[List[dict]],
                         css_dict: Dict[str, _CSSPropsWithIndex],
                         ) -> List[List[HTMLPropsTableRowElement]]:
        # - ids are removed from the entries, otherwise they have to be re-indexed to be unique when combining chunks
        # - non-visible elements are removed
        # - "rowspan" and "colspan" are resolved
        # - trailing blank headers are removed
        # - css classes are replaced by the computed css properties, otherwise css classes
        # would have to be re-indexed to be unique when combining chunks
        transformed_rows: List[List[HTMLPropsTableRowElement]] = []
        open_spans: Dict[int, List[_SpannedElement]] = {}

        for row in rows:

            transformed_row = []
            ignore_trailing_blank_row_headers = False

            for ci, element in enumerate(row):

                element_to_add = None
                element_classes_set = set(element.get("class", "").split(" "))
                is_header = element.get("type", "") == "th"

                if ignore_trailing_blank_row_headers:
                    if is_header and "blank" in element_classes_set:
                        continue
                else:
                    ignore_trailing_blank_row_headers = is_header and "blank" not in element_classes_set

                if element.get("is_visible", True):
                    transformed_element = self.__transform_row_element(
                        element,
                        element_classes_set,
                        css_dict,
                    )
                    element_to_add = transformed_element

                    spanned_element = self.__create_spanned_element_from_span_attributes(transformed_element)
                    if spanned_element is not None:
                        element_to_add = None
                        open_spans.setdefault(ci, []).append(spanned_element)

                if ci in open_spans:
                    pending_col_spans = open_spans[ci]
                    remove_consumed_spans = False
                    for pending_span in pending_col_spans:
                        transformed_row.extend(pending_span.col_span * [pending_span.element])
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
                    transformed_row.append(element_to_add)

            if len(transformed_row) > 0:
                transformed_rows.append(transformed_row)

        assert len(open_spans) == 0, f"Trailing spans are not implemented, found {len(open_spans)} pending."
        return transformed_rows

    def __transform_row_element(self,
                                element: dict,
                                element_classes_set: Set[str],
                                css_dict: Dict[str, _CSSPropsWithIndex],
                                ) -> HTMLPropsTableRowElement:
        return HTMLPropsTableRowElement(
            type=element.get("type", ""),
            # convert to string (value can be tuple, dict, etc.)
            display_value=str(element.get("display_value", "")),
            kind=self.__get_kind(element_classes_set),
            css_props=self.__compute_element_css(element, element_classes_set, css_dict),
            attributes=self.__extract_attributes(element),
        )

    @staticmethod
    def __compute_element_css(
            element: dict,
            element_classes_set: Set[str],
            css_dict: Dict[str, _CSSPropsWithIndex],
    ) -> Optional[Dict[str, str]]:
        matching_css_props: List[_CSSPropsWithIndex] = []

        for c in element_classes_set:
            css_props = css_dict.get(c, None)
            if css_props is not None:
                matching_css_props.append(css_props)
        if len(matching_css_props) > 1:
            # sort highest index last
            matching_css_props.sort(key=lambda x: x.index)

        id_css_props = css_dict.get(element.get("id", None), None)
        if id_css_props is not None:
            # props for id selector have higher priority over class selectors
            matching_css_props.append(id_css_props)

        if len(matching_css_props) == 0:
            return None

        result: Dict[str, str] = {}
        for css_props in matching_css_props:
            # keep it simple for now - don't handle "!important" and other stuff
            result.update(css_props.props)
        return result

    @staticmethod
    def __get_kind(element_classes_set: Set[str]) -> str:
        if "blank" in element_classes_set:
            return "blank"
        if "index_name" in element_classes_set:
            return "index_name"
        if "col_heading" in element_classes_set:
            return "col_heading"
        if "row_heading" in element_classes_set:
            return "row_heading"
        return ""

    @staticmethod
    def __create_spanned_element_from_span_attributes(element: HTMLPropsTableRowElement) -> Optional[_SpannedElement]:
        attributes = element.attributes
        if attributes is None:
            return None

        rowspan = attributes.pop("rowspan", None)
        colspan = attributes.pop("colspan", None)
        if len(attributes) == 0:
            element.attributes = None
        if rowspan is None and colspan is None:
            return None

        rowspan = 1 if rowspan is None else int(rowspan)
        colspan = 1 if colspan is None else int(colspan)
        if rowspan > 1 or colspan > 1:
            return _SpannedElement(rowspan, colspan, element)

        return None

    @staticmethod
    def __extract_attributes(element: dict) -> Optional[Dict[str, Any]]:
        attributes = element.get("attributes", '')  # empty attributes can be defined as an empty string instead of list
        if attributes == '':
            return None
        if isinstance(attributes, list):
            if len(attributes) == 0:
                return None
            attributes_dict = {}
            for attr in attributes:
                key, value = attr.split("=")
                # note: most attributes are formatted like: 'rowspan="2"' (extra quotes around the value)
                # but sometimes pandas also formats attributes like: 'colspan=2' (no extra quotes around the value)
                # -> instead of "value[1:-1]" use "value.strip('"')"
                attributes_dict[key] = value.strip('"')
            return attributes_dict
        return None
