from cms_rendner_sdfv.base.types import CellMeta, TextAlign


def test_flags_only():
    meta = CellMeta()
    assert meta.pack() == 'FFF||||'

    meta = CellMeta(is_nan=True)
    assert meta.pack() == 'TFF||||'

    meta = CellMeta(is_min=True)
    assert meta.pack() == 'FTF||||'

    meta = CellMeta(is_max=True)
    assert meta.pack() == 'FFT||||'

    meta = CellMeta(is_min=True, is_max=True)
    assert meta.pack() == 'FTT||||'


def test_optional_values():
    meta = CellMeta(cmap_value=123)
    assert meta.pack() == 'FFF123||||'

    meta = CellMeta(background_color='red')
    assert meta.pack() == 'FFF||red||'

    meta = CellMeta(text_color='red')
    assert meta.pack() == 'FFF|||red|'

    meta = CellMeta(text_align=TextAlign.CENTER)
    assert meta.pack() == 'FFF|C|||'

    meta = CellMeta(cmap_value=123, text_align=TextAlign.CENTER)
    assert meta.pack() == 'FFF123|C|||'


def test_from_packed():
    assert CellMeta.from_packed('FFF123||||') == CellMeta(cmap_value=123)
    assert CellMeta.from_packed('FFF||red||') == CellMeta(background_color='red')
    assert CellMeta.from_packed('FFF|||red|') == CellMeta(text_color='red')
    assert CellMeta.from_packed('FFF|C|||') == CellMeta(text_align=TextAlign.CENTER)
    assert CellMeta.from_packed('FFF123|C|||') == CellMeta(cmap_value=123, text_align=TextAlign.CENTER)
