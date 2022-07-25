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
from __future__ import annotations

from html.parser import HTMLParser
from typing import List, Dict, Tuple, Union

import cssutils


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
        self.table: Element = Element("table", {})
        self.styles: Dict[str, str] = {}


class ExtractContext:
    def __init__(self):
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

    def handle_starttag(self, tag: str, attrs: List[Tuple[str, str]]):
        element = Element(tag, dict(attrs))
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
            if self.starttag_is_style_tag():
                self.ctx.styled_table.styles = self.__build_selector_map(data)
            else:
                self.ctx.open_elements_stack[-1].text = data

    def starttag_is_style_tag(self):
        # the style tag in pandas 1.2.5 looks like '<style  type="text/css" >' (note the extra spaces)
        # -> strip out all spaces before comparing it
        return self.get_starttag_text().replace(" ", "") == '<style type="text/css">'.replace(" ", "")

    @staticmethod
    def __build_selector_map(html_stylesheet: str) -> Dict[str, str]:
        result = {}
        for rule in cssutils.parseString(html_stylesheet):
            if rule.type == rule.STYLE_RULE:
                for selector in rule.selectorList:
                    selector_name = selector.selectorText
                    first_char = selector_name[0:1]
                    if first_char != "#":
                        raise Exception('Unknown selector not handled yet.')
                    result[selector_name] = rule.style.cssText
        return result
