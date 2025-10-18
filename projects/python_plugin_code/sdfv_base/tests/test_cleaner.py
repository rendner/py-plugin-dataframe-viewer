from dataclasses import dataclass

import pytest

from cms_rendner_sdfv.base.temp import TEMP_VARS, Cleaner


def test_cleaner_exist():
    assert isinstance(TEMP_VARS["_cleaner_"], Cleaner)


def test_cleaner_deletes_slot_vars():
    @dataclass
    class A:
        pass

    value_a = A()
    TEMP_VARS["slot_a"] = value_a
    TEMP_VARS["slot_b"] = 12
    TEMP_VARS["slot_c"] = "hello"

    TEMP_VARS["_cleaner_"].free_slots(["slot_a", "slot_b"])

    with pytest.raises(KeyError, match="slot_a"):
        TEMP_VARS["slot_a"]

    with pytest.raises(KeyError, match="slot_b"):
        TEMP_VARS["slot_b"]

    assert TEMP_VARS["slot_c"] == "hello"
