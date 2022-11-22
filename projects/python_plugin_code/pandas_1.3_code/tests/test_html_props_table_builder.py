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
import pytest

from plugin_code.html_props_table_builder import HTMLPropsTableBuilder


def test_empty_builder_creates_empty_table():
    table = HTMLPropsTableBuilder().build()
    assert len(table.head) == 0
    assert len(table.body) == 0


def test_resolves_rowspan_and_colspan_for_visible_head_elements():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "A"},
                # note: attributes values are all quoted: 'rowspan="2"' (extra quotes around the value)
                {"type": "th", "is_visible": True, "attributes": ['rowspan="2"', 'colspan="2"'], "display_value": "B"},
                {"type": "th", "is_visible": True, "display_value": "C"},
            ],
            [
                {"type": "th", "is_visible": True, "display_value": "X"},
                {"type": "th", "is_visible": True, "display_value": "Y"},
            ]
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 2

    first_row = table.head[0]
    assert len(first_row) == 4
    assert list(map(lambda e: e.display_value, first_row)) == ["A", "B", "B", "C"]
    assert list(map(lambda e: e.attributes, first_row)) == [None, None, None, None]

    second_row = table.head[1]
    assert len(second_row) == 4
    assert list(map(lambda e: e.display_value, second_row)) == ["X", "B", "B", "Y"]
    assert list(map(lambda e: e.attributes, second_row)) == [None, None, None, None]


def test_raises_exception_for_trailing_rowspan_and_colspan():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "A"},
                # note: attributes values are all quoted: 'rowspan="2"' (extra quotes around the value)
                {"type": "th", "is_visible": True, "attributes": ['rowspan="2"', 'colspan="2"'], "display_value": "B"},
            ],
        ],
    }

    msg = "Trailing spans are not implemented, found 1 pending."
    with pytest.raises(AssertionError, match=msg):
        table_builder.append_props(html_props, 0, True, True)


def test_ignores_rowspan_and_colspan_for_non_visible_head_elements():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "A"},
                # note: attributes values are all quoted: 'rowspan="2"' (extra quotes around the value)
                {"type": "th", "is_visible": False, "attributes": ['rowspan="2"', 'colspan="2"'], "display_value": "B"},
                {"type": "th", "is_visible": True, "display_value": "C"},
            ],
            [
                {"type": "th", "is_visible": True, "display_value": "X"},
                {"type": "th", "is_visible": True, "display_value": "Y"},
            ]
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 2
    assert len(table.head[0]) == 2
    assert len(table.head[1]) == 2
    assert list(map(lambda e: e.display_value, table.head[0])) == ["A", "C"]
    assert list(map(lambda e: e.display_value, table.head[1])) == ["X", "Y"]


def test_resolves_rowspan_and_colspan_for_visible_body_elements():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "body": [
            [
                {"type": "td", "is_visible": True, "display_value": "A"},
                # note: attributes values are all quoted: 'rowspan="2"' (extra quotes around the value)
                {"type": "td", "is_visible": True, "attributes": ['rowspan="2"', 'colspan="2"'], "display_value": "B"},
                {"type": "td", "is_visible": True, "display_value": "C"},
            ],
            [
                {"type": "td", "is_visible": True, "display_value": "X"},
                {"type": "td", "is_visible": True, "display_value": "Y"},
            ]
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.body) == 2

    first_row = table.body[0]
    assert len(first_row) == 4
    assert list(map(lambda e: e.display_value, first_row)) == ["A", "B", "B", "C"]
    assert list(map(lambda e: e.attributes, first_row)) == [None, None, None, None]

    second_row = table.body[1]
    assert len(second_row) == 4
    assert list(map(lambda e: e.display_value, second_row)) == ["X", "B", "B", "Y"]
    assert list(map(lambda e: e.attributes, second_row)) == [None, None, None, None]


def test_ignores_rowspan_and_colspan_for_non_visible_body_elements():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "body": [
            [
                {"type": "td", "is_visible": True, "display_value": "A"},
                # note: attributes values are all quoted: 'rowspan="2"' (extra quotes around the value)
                {"type": "td", "is_visible": False, "attributes": ['rowspan="2"', 'colspan="2"'], "display_value": "B"},
                {"type": "td", "is_visible": True, "display_value": "C"},
            ],
            [
                {"type": "td", "is_visible": True, "display_value": "X"},
                {"type": "td", "is_visible": True, "display_value": "Y"},
            ]
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.body) == 2
    assert len(table.body[0]) == 2
    assert len(table.body[1]) == 2
    assert list(map(lambda e: e.display_value, table.body[0])) == ["A", "C"]
    assert list(map(lambda e: e.display_value, table.body[1])) == ["X", "Y"]


def test_ignores_non_visible_elements():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "A"},
                {"type": "th", "is_visible": False, "display_value": "B"},
                {"type": "th", "is_visible": True, "display_value": "C"},
            ],
        ],
        "body": [
            [
                {"type": "td", "is_visible": True, "display_value": "X"},
                {"type": "td", "is_visible": True, "display_value": "Y"},
                {"type": "td", "is_visible": False, "display_value": "Z"},
            ]
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 1
    assert len(table.head[0]) == 2
    assert list(map(lambda e: e.display_value, table.head[0])) == ["A", "C"]

    assert len(table.body) == 1
    assert len(table.body[0]) == 2
    assert list(map(lambda e: e.display_value, table.body[0])) == ["X", "Y"]


def test_removes_trailing_blank_headers():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "", "class": "blank"},
                {"type": "th", "is_visible": True, "display_value": "A"},
                {"type": "th", "is_visible": True, "display_value": "B"},
                {"type": "th", "is_visible": True, "display_value": "", "class": "blank"},
                {"type": "th", "is_visible": True, "display_value": "", "class": "blank"},
                {"type": "th", "is_visible": True, "display_value": "", "class": "blank"},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 1
    assert len(table.head[0]) == 3
    assert list(map(lambda e: e.display_value, table.head[0])) == ["", "A", "B"]


def test_css_is_correctly_resolved():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "cellstyle": [
            # note: selectors are without leading "#" and ".", therefore it isn't clear if they belong to class or id
            {"props": [("color", "yellow")], "selectors": ["1234"]},
            {"props": [("color", "red")], "selectors": ["col0", "col1"]},
            {"props": [("color", "pink")], "selectors": ["special"]},
        ],
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "A", "class": "col0 special"},
                {"type": "th", "is_visible": True, "display_value": "B", "class": "col1"},
                {"type": "th", "is_visible": True, "display_value": "C", "id": "1234", "class": "col2"},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 1
    assert len(table.head[0]) == 3
    assert list(map(lambda e: e.display_value, table.head[0])) == ["A", "B", "C"]
    assert list(map(lambda e: e.css_props, table.head[0])) == [{'color': 'pink'}, {'color': 'red'}, {'color': 'yellow'}]


def test_element_kind__blank():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "", "class": "blank col0"},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 1
    assert len(table.head[0]) == 1
    assert table.head[0][0].kind == "blank"


def test_element_kind__index_name():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "", "class": "index_name level0"},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 1
    assert len(table.head[0]) == 1
    assert table.head[0][0].kind == "index_name"


def test_element_kind__col_heading():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "head": [
            [
                {"type": "th", "is_visible": True, "display_value": "", "class": "col_heading level0 col0"},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.head) == 1
    assert len(table.head[0]) == 1
    assert table.head[0][0].kind == "col_heading"


def test_element_kind__row_heading():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "body": [
            [
                {"type": "th", "is_visible": True, "display_value": "", "class": "row_heading level0 row0"},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.body) == 1
    assert len(table.body[0]) == 1
    assert table.body[0][0].kind == "row_heading"


def test_unknown_element_kind():
    table_builder = HTMLPropsTableBuilder()
    html_props = {
        "body": [
            [
                {"type": "th", "is_visible": True, "display_value": "", "class": "level0 row0"},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.body) == 1
    assert len(table.body[0]) == 1
    assert table.body[0][0].kind == ""


def test_truncates_too_long_display_values():
    table_builder = HTMLPropsTableBuilder()
    value = "abc" * HTMLPropsTableBuilder.APPROX_DISPLAY_VALUE_LENGTH
    html_props = {
        "body": [
            [
                {"type": "td", "is_visible": True, "display_value": value},
            ],
        ],
    }

    table_builder.append_props(html_props, 0, True, True)
    table = table_builder.build()

    assert len(table.body) == 1
    assert len(table.body[0]) == 1
    assert len(table.body[0][0].display_value) < len(value)
