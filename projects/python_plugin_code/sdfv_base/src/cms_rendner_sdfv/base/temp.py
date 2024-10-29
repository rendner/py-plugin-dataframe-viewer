import inspect
from typing import List

TEMP_VARS = {}


class EvaluatedVarsCleaner:

    @staticmethod
    def clear(id_names: List[str], frame_offset: int = 0):
        not_found = []
        names_to_check = id_names

        for name in names_to_check:
            temp_var = TEMP_VARS.pop(name, None)
            if temp_var is None:
                not_found.append(name)
            else:
                if hasattr(temp_var, 'unlink'):
                    temp_var.unlink()

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
                    local_var = f_locals[name]
                    f_locals[name] = None
                    if hasattr(local_var, 'unlink'):
                        local_var.unlink()
                else:
                    not_found.append(name)

            if not not_found:
                return

            frame = frame.f_back
            if frame is None:
                return
