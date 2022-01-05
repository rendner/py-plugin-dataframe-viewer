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
from __future__ import annotations

from html.parser import HTMLParser
from typing import List, Dict, Tuple, Union

import cssutils


class IndexTranslator:
    def translate(self, index):
        return index


class SequenceIndexTranslator(IndexTranslator):
    def __init__(self, seq):
        super().__init__()
        self.__seq = seq

    def translate(self, index):
        return self.__seq[index]

    def __str__(self):
        return f'seq:{str(self.__seq)}'


class OffsetIndexTranslator(IndexTranslator):
    def __init__(self, offset: int):
        super().__init__()
        self.__offset = offset

    def translate(self, index):
        return index + self.__offset

    def __str__(self):
        return f'offset:{self.__offset}'


class Element:
    def __init__(self, tag: str, attrs: Dict[str, str]):
        self.tag: str = tag
        self.text: str = ''
        self.attrs: Dict[str, str] = attrs
        self.children: List[Element] = []

    def has_class(self, class_: str) -> bool:
        return class_ in self.attrs.get("class", [])

    def find_first(self, tag: str) -> Union[Element, None]:
        for child in self.children:
            if child.tag == tag:
                return child
        return None

    def find(self, tag: str) -> List[Element]:
        result = []
        for child in self.children:
            if child.tag == tag:
                result.append(child)
        return result

    def __str__(self):
        return f'tag:{self.tag}, attrs:{str(self.attrs)}, #-children:{len(self.children)}, text:{self.text}'

    def __eq__(self, obj):
        return isinstance(obj, Element) \
               and obj.tag == self.tag \
               and obj.text == self.text \
               and obj.attrs == self.attrs \
               and obj.children == self.children


class StyledTable:
    def __init__(self):
        self.table: Element = None
        self.styles: Dict[str, str] = {}


def _create_index_translator(content: str) -> IndexTranslator:
    if content.startswith("["):
        return SequenceIndexTranslator(list(map(int, content[1:-1].split(" "))))
    else:
        return OffsetIndexTranslator(int(content))


class ExtractContext:
    def __init__(self):
        super().__init__()
        self.row_indexer = IndexTranslator()
        self.col_indexer = IndexTranslator()
        self.adjusted_ids = {}
        self.styled_table = StyledTable()
        self.open_elements_stack = []


class TableExtractor(HTMLParser):

    def __init__(self):
        super().__init__()
        self.ctx: ExtractContext = ExtractContext()

    def extract(self, html: str):
        self.reset()
        self.feed(html)
        return self.ctx.styled_table

    def reset(self):
        super().reset()
        self.ctx = ExtractContext()

    def error(self, message: str):
        print(message)

    def handle_startendtag(self, tag: str, attrs: List[Tuple[str, str]]):
        if tag == "meta":
            meta_attrs = dict(attrs)
            if meta_attrs["name"] == "row_indexer":
                self.ctx.row_indexer = _create_index_translator(meta_attrs["content"])
            if meta_attrs["name"] == "col_indexer":
                self.ctx.col_indexer = _create_index_translator(meta_attrs["content"])

    def handle_starttag(self, tag: str, attrs: List[Tuple[str, str]]):
        element = Element(tag, self.__adjust_attrs(attrs))
        if len(self.ctx.open_elements_stack) != 0:
            self.ctx.open_elements_stack[-1].children.append(element)

        self.ctx.open_elements_stack.append(element)
        if tag == "table":
            self.ctx.styled_table.table = element

    def handle_endtag(self, tag: str):
        self.ctx.open_elements_stack.pop()

    def handle_data(self, data: str):
        data = data.strip()
        if len(data) != 0:
            if self.get_starttag_text() == '<style type="text/css">':
                self.ctx.styled_table.styles = self.__build_selector_map(data)
            else:
                self.ctx.open_elements_stack[-1].text = data

    def __build_selector_map(self, html_stylesheet: str) -> Dict[str, str]:
        result = {}
        for rule in cssutils.parseString(html_stylesheet):
            if rule.type == rule.STYLE_RULE:
                for selector in rule.selectorList:
                    selector_name = selector.selectorText
                    first_char = selector_name[0:1]
                    if first_char == "#":
                        id_name = selector_name[1:]
                        if id_name in self.ctx.adjusted_ids:
                            selector_name = self.ctx.adjusted_ids[id_name]
                        else:
                            updated_id_name = "_".join(self.__adjust_indices(id_name.split("_")))
                            self.ctx.adjusted_ids[id_name] = updated_id_name
                            selector_name = f'#{updated_id_name}'
                    elif first_char == ".":
                        raise Exception('class selectors not handled yet.')
                        pass
                    else:
                        raise Exception('Unknown selector not handled yet.')
                        pass
                    result[selector_name] = rule.style.cssText
        return result

    def __adjust_attrs(self, attrs: List[Tuple[str, str]]) -> Dict[str, str]:
        result = {}
        for key, value in attrs:
            if value is None:
                value = ''

            if key == "id":
                if value in self.ctx.adjusted_ids:
                    value = self.ctx.adjusted_ids[value]
                else:
                    updated_id = '_'.join(self.__adjust_indices(value.split('_')))
                    self.ctx.adjusted_ids[value] = updated_id
                    value = updated_id
            elif key == "class":
                value = ' '.join(self.__adjust_indices(value.strip().split(' ')))

            result[key] = value
        return result

    def __adjust_indices(self, indices: List[str]) -> List[str]:
        return list(map(self.__adjust_index, indices))

    def __adjust_index(self, index: str) -> str:
        if index.startswith("row") and index[3:].isdigit():
            return f'row{self.ctx.row_indexer.translate(int(index[3:]))}'
        if index.startswith("col") and index[3:].isdigit():
            return f'col{self.ctx.col_indexer.translate(int(index[3:]))}'
        return index
