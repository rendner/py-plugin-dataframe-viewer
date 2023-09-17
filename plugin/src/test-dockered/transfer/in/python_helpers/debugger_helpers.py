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

import inspect
import traceback
import os


class DebuggerInternals:

    __id_counter = 0
    __excluded_result_types = (int, float, str, bool)
    __traces = []
    __trace_step = 0

    @staticmethod
    def eval(expression):
        if isinstance(expression, str):
            expression = DebuggerInternals.__unescape(expression)

        callers_frame = inspect.currentframe().f_back

        DebuggerInternals.__trace_input(expression, callers_frame)

        try:
            result = eval(expression, callers_frame.f_globals, callers_frame.f_locals)
        except BaseException as exc:
            DebuggerInternals.__print_exception(exc)
            return

        DebuggerInternals.__trace_output(result)

        if isinstance(result, str):
            result = DebuggerInternals.__escape(result)

        ref_key = None
        if result is not None and not isinstance(result, DebuggerInternals.__excluded_result_types):
            # in case expression was only an existing identifier
            if expression in callers_frame.f_locals:
                ref_key = expression
            else:
                # store created object and make it accessible
                ref_key = f'__dbg_ref_id_{DebuggerInternals.__id_counter}'
                DebuggerInternals.__id_counter += 1
                callers_frame.f_locals[ref_key] = result
        print(f'@_@RESULT@_@{DebuggerInternals.__full_type(result)} {ref_key} {result}@_@RESULT@_@')

    @staticmethod
    def exec(value):
        if isinstance(value, str):
            value = DebuggerInternals.__unescape(value)

        callers_frame = inspect.currentframe().f_back

        DebuggerInternals.__trace_input(value, callers_frame)

        try:
            exec(value, callers_frame.f_globals, callers_frame.f_locals)
        except BaseException as exc:
            DebuggerInternals.__print_exception(exc)

    @staticmethod
    def __full_type(o) -> str:
        klass = getattr(o, '__class__', '')
        module = getattr(klass, '__module__', '')
        qname = getattr(klass, '__qualname__', '')
        return f'{module}:{qname}'

    @staticmethod
    def __print_exception(exc):
        DebuggerInternals.__trace_output(exc)
        print(traceback.format_exc())
        print(f'@_@EXC@_@{DebuggerInternals.__full_type(exc)} {exc}@_@EXC@_@')

    @staticmethod
    def __unescape(value: str) -> str:
        return value.replace("@_@NL@_@", "\n")

    @staticmethod
    def __escape(value: str) -> str:
        return value.replace("\n", "@_@NL@_@")

    @staticmethod
    def __trace_input(value: str, frame):
        DebuggerInternals.__trace_step += 1
        if DebuggerInternals.__trace_step > 1:
            DebuggerInternals.__traces.append("\n")

        if frame:
            frame_index = 0
            f = frame
            while f and f.f_back:
                frame_index += 1
                f = f.f_back
            DebuggerInternals.__traces.append(f"frame_index: {frame_index}")
            DebuggerInternals.__traces.append(f"locals: {str(frame.f_locals)}")
        else:
            DebuggerInternals.__traces.append(f"frame_index: ?")
            DebuggerInternals.__traces.append(f"locals: ?")

        DebuggerInternals.__traces.append(f"in[{DebuggerInternals.__trace_step}]: {value}")

    @staticmethod
    def __trace_output(value: str):
        DebuggerInternals.__traces.append(f"out[{DebuggerInternals.__trace_step}]: {value}")

    @staticmethod
    def dump_traces_on_exit(file_path: str):
        import signal
        import atexit
        from pathlib import Path

        def write_to_file():
            try:
                # remove restrictions - no need to restore old mask afterward (is executed on exit)
                os.umask(0)

                file = Path(f"{os.environ['TRANSFER_OUT_DIR']}/{file_path}")
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("\n".join(DebuggerInternals.__traces))

            except BaseException as exc:
                print(exc)

        atexit.register(write_to_file)
        signal.signal(signal.SIGINT, write_to_file)
        signal.signal(signal.SIGTERM, write_to_file)
