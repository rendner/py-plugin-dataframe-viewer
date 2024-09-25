import inspect
from typing import List

TEMP_VARS = {}


class EvaluatedVarsCleaner:

    @staticmethod
    def clear(id_names: List[str], frame_offset: int = 0):
        not_found = []
        names_to_check = id_names

        for name in names_to_check:
            if TEMP_VARS.pop(name, None) is None:
                not_found.append(name)

        if not not_found:
            return

        frame = inspect.currentframe().f_back
        if frame is None:
            return

        for i in range(frame_offset):
            frame = frame.f_back
            if frame is None:
                return

        for i in range(10):
            names_to_check = not_found
            not_found = []
            f_locals = frame.f_locals

            for name in names_to_check:
                if name in f_locals:
                    f_locals[name] = None
                else:
                    not_found.append(name)

            if not not_found:
                return

            frame = frame.f_back
            if frame is None:
                return
