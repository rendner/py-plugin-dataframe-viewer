#  Copyright 2023 cms.rendner (Daniel Schmidt)
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
import pandas as pd

from plugin_code.create_fingerprint import create_fingerprint

df_dict = {
    "col_0": [4, 4, 4, 1, 4],
    "col_1": [1, 4, 4, 1, 2],
}

df = pd.DataFrame.from_dict(df_dict)


def test_two_different_styler_but_same_frame_have_same_fingerprint():
    f1 = create_fingerprint(df.style.highlight_max())
    f2 = create_fingerprint(df.style.highlight_min())
    assert f1 == f2

    f1 = create_fingerprint(df.style)
    f2 = create_fingerprint(df.style)
    assert f1 == f2


def test_same_frame_have_same_fingerprint():
    f1 = create_fingerprint(df)
    f2 = create_fingerprint(df)
    assert f1 == f2

    f1 = create_fingerprint(df)
    f2 = create_fingerprint(df.style.data)
    assert f1 == f2


def test_different_frames_have_different_fingerprints():
    a = pd.DataFrame.from_dict({"A": [1]})
    b = pd.DataFrame.from_dict({"A": [1]})
    f1 = create_fingerprint(a)
    f2 = create_fingerprint(b)
    assert f1 != f2

    a = pd.DataFrame.from_dict({"A": [1]})
    b = pd.DataFrame.from_dict({"A": [1]}).style
    f1 = create_fingerprint(a)
    f2 = create_fingerprint(b)
    assert f1 != f2


def test_different_frames_with_same_source_have_same_fingerprint():
    a = pd.DataFrame.from_dict(df_dict)
    b = pd.DataFrame.from_dict(df_dict)
    f1 = create_fingerprint(a, df_dict)
    f2 = create_fingerprint(b, df_dict)
    assert f1 == f2

    a = pd.DataFrame.from_dict(df_dict)
    b = pd.DataFrame.from_dict(df_dict).style
    f1 = create_fingerprint(a, df_dict)
    f2 = create_fingerprint(b, df_dict)
    assert f1 == f2
