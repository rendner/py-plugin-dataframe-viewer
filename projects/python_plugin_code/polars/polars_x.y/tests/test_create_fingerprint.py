import polars as pl

from cms_rendner_sdfv.polars.create_fingerprint import create_fingerprint

df_dict = {
    "0": [0, 1, 2],
    "1": [3, 4, 5],
}

df = pl.DataFrame(df_dict)


def test_same_frames_have_same_fingerprint():
    f1 = create_fingerprint(df)
    f2 = create_fingerprint(df)
    assert f1 == f2


def test_different_frames_have_different_fingerprints():
    d = {"A": [1]}
    a = pl.from_dict(d)
    b = pl.from_dict(d)
    f1 = create_fingerprint(a)
    f2 = create_fingerprint(b)
    assert f1 != f2


def test_different_frames_with_same_source_have_same_fingerprint():
    a = pl.from_dict(df_dict)
    b = pl.from_dict(df_dict)
    f1 = create_fingerprint(a, df_dict)
    f2 = create_fingerprint(b, df_dict)
    assert f1 == f2
