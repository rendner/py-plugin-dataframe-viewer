from pandas.io.formats.style import Styler

from cms_rendner_sdfv.pandas.styler.styler_todo import StylerTodo


def extract_first_todo(styler: Styler) -> StylerTodo:
    return StylerTodo.from_tuple(0, styler._todo[0])
