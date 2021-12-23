#  Copyright 2021 cms.rendner (Daniel Schmidt)
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
def collect_all_test_cases(root_dir: str):
    import os
    import pandas as pd

    test_cases = []

    for dirpath, dirnames, filenames in os.walk(root_dir):
        for name in dirnames:
            test_cases.extend(collect_test_cases_from_dir(os.path.join(dirpath, name)))

    return {"test_cases": test_cases, "pandas_version": pd.__version__}


def collect_test_cases_from_dir(module_dir: str):
    import os
    from pathlib import Path
    from importlib import import_module

    test_cases = []
    py_suffix_length = len(".py")
    module_path_start_offset = module_dir.index("export_data")
    export_dir_name_offset = module_path_start_offset + len("export_data.")

    for f in Path(module_dir).glob("*.py"):
        module_name = f.stem
        if not module_name.startswith('_'):
            path = str(f)
            module_import_name = path[export_dir_name_offset:-py_suffix_length].replace(os.sep, '.')
            module = import_module(module_import_name)
            if hasattr(module, 'test_case'):
                test_case = getattr(module, 'test_case')
                test_case_export_dir = path[export_dir_name_offset:-py_suffix_length]
                test_cases.append({**test_case, 'export_dir': test_case_export_dir})

    return test_cases
