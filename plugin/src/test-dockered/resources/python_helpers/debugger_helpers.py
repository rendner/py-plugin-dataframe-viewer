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

# Code changes in this file require a rebuild of the python docker images (gradle task "buildPythonDockerImages").
import inspect
import threading
import traceback


class DebuggerInternals:

    __threadLock = threading.Lock()
    __id_counter = 0
    __excluded_result_types = (int, float, str, bool)

    @staticmethod
    def eval(expression):
        if isinstance(expression, str):
            expression = DebuggerInternals.__unescape(expression)

        # get the caller's frame
        previous_frame = inspect.currentframe().f_back

        try:
            result = eval(expression, previous_frame.f_globals, previous_frame.f_locals)
        except BaseException as exc:
            DebuggerInternals.__print_exception(exc)
            return

        if isinstance(result, str):
            result = DebuggerInternals.__escape(result)

        ref_key = None
        if result is not None and not isinstance(result, DebuggerInternals.__excluded_result_types):
            # in case expression was only an existing identifier
            if expression in previous_frame.f_locals:
                ref_key = expression
            else:
                # store created object and make it accessible
                ref_key = f'__dbg_ref_id_{DebuggerInternals.__get_id_and_increment()}'
                previous_frame.f_locals[ref_key] = result
        print(f'@_@RESULT@_@{DebuggerInternals.__full_type(result)} {ref_key} {result}@_@RESULT@_@')

    @staticmethod
    def exec(value):
        if isinstance(value, str):
            value = DebuggerInternals.__unescape(value)
        # get the caller's frame
        previous_frame = inspect.currentframe().f_back

        try:
            exec(value, previous_frame.f_globals, previous_frame.f_locals)
        except BaseException as exc:
            DebuggerInternals.__print_exception(exc)

    @staticmethod
    def __get_id_and_increment() -> int:
        with DebuggerInternals.__threadLock:
            id = DebuggerInternals.__id_counter
            DebuggerInternals.__id_counter += 1
            return id

    @staticmethod
    def __full_type(o) -> str:
        klass = getattr(o, '__class__', '')
        module = getattr(klass, '__module__', '')
        qname = getattr(klass, '__qualname__', '')
        return f'{module}:{qname}'

    @staticmethod
    def __print_exception(exc):
        print(traceback.format_exc())
        print(f'@_@EXC@_@{DebuggerInternals.__full_type(exc)} {exc}@_@EXC@_@')

    @staticmethod
    def __unescape(value: str) -> str:
        return value.replace("@_@NL@_@", "\n")

    @staticmethod
    def __escape(value: str) -> str:
        return value.replace("\n", "@_@NL@_@")
