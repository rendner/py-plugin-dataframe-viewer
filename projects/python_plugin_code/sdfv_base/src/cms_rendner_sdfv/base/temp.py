from typing import List

TEMP_VARS = {}


class Cleaner:
    @staticmethod
    def free_slots(slot_ids: List[str]):
        for slot_id in slot_ids:
            value = TEMP_VARS.pop(slot_id, None)
            if hasattr(value, 'unlink'):
                try:
                    value.unlink()
                except Exception:
                    pass


TEMP_VARS["_cleaner_"] = Cleaner()
