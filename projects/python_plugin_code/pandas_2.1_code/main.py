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
PACKAGE_NAME = "plugin_code"
COPY_MARKER = "# == copy after here =="


# todo: strip out comments and docstrings -> allows us to document the source code
# https://stackoverflow.com/questions/1769332/script-to-remove-python-comments-docstrings/1769577#1769577
# use "astunparse" to implement it

def generate_plugin_code():
    # the order of the files matters (Python does not allow calling of a function/class before declaring it)
    file_names = [
        'create_fingerprint.py',
        'custom_json_encoder.py',
        'styler_todo.py',
        'style_function_name_resolver.py',
        'todo_patcher.py',
        'map_patcher.py',
        'apply_patcher.py',
        'background_gradient_patcher.py',
        'chunk_parent_provider.py',
        'highlight_between_patcher.py',
        'highlight_extrema_patcher.py',
        'todos_patcher.py',
        'patched_styler_context.py',
        'display_value_truncator.py',
        'table_frame_generator.py',
        'table_frame_validator.py',
        'style_functions_validator.py',
        'patched_styler.py',
        'styled_data_frame_viewer_bridge.py',
    ]
    with open('generated/plugin_code', 'w', encoding='utf8', newline='\n') as outfile:
        for file_name in file_names:
            with open(f"{PACKAGE_NAME}/{file_name}", encoding='utf8', newline='\n') as infile:
                copy_marker_found = False
                for line in infile:
                    stripped_line = line.strip()
                    if copy_marker_found:
                        if not stripped_line.startswith("#"):
                            if stripped_line.startswith("print"):
                                print(f"WARN: print found in code:\n\t{stripped_line}")
                            if f"from {PACKAGE_NAME}" in stripped_line:
                                # import is not required in the final plugin code
                                print(f"ERROR: wrong placed import in file '{file_name}' - move above copy marker: {stripped_line}")
                                return
                            outfile.write(line)
                    else:
                        if stripped_line.startswith(COPY_MARKER):
                            copy_marker_found = True
                            outfile.write("\n")
                        elif not stripped_line.startswith("#"):
                            if any(s in stripped_line for s in
                                   ["from", "import"]) and f"{PACKAGE_NAME}" not in stripped_line:
                                # import is required in the final plugin code
                                print(f"ERROR: wrong placed import in file '{file_name}' - move below copy marker: {stripped_line}")
                                return
                if not copy_marker_found:
                    print(f"ERROR: specified file '{file_name}' doesn't contain a copy marker '{COPY_MARKER}'")
    print("\ndone")


if __name__ == "__main__":
    generate_plugin_code()
