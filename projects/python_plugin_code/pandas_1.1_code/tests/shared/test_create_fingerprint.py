import pandas as pd

from cms_rendner_sdfv.pandas.shared.create_fingerprint import create_fingerprint

df_dict = {
    "col_0": [4, 4, 4, 1, 4],
    "col_1": [1, 4, 4, 1, 2],
}

df = pd.DataFrame.from_dict(df_dict)


def test_same_frames_have_same_fingerprint():
    f1 = create_fingerprint(df)
    f2 = create_fingerprint(df)
    assert f1 == f2


def test_different_frames_have_different_fingerprints():
    a = pd.DataFrame.from_dict({"A": [1]})
    b = pd.DataFrame.from_dict({"A": [1]})
    f1 = create_fingerprint(a)
    f2 = create_fingerprint(b)
    assert f1 != f2


def test_different_frames_with_same_source_have_same_fingerprint():
    a = pd.DataFrame.from_dict(df_dict)
    b = pd.DataFrame.from_dict(df_dict)
    f1 = create_fingerprint(a, df_dict)
    f2 = create_fingerprint(b, df_dict)
    assert f1 == f2
