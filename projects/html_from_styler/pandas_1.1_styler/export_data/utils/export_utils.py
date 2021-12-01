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
    export_dir_name_offset = module_path_start_offset + len("export_data")

    for f in Path(module_dir).glob("*.py"):
        module_name = f.stem
        if not module_name.startswith('_'):
            path = str(f)
            module_import_name = path[module_path_start_offset:-py_suffix_length].replace(os.sep, '.')
            module = import_module(module_import_name)
            if hasattr(module, 'test_case'):
                test_case = getattr(module, 'test_case')
                test_case_export_dir = path[export_dir_name_offset + 1:-py_suffix_length]
                test_cases.append({**test_case, 'export_dir': test_case_export_dir})

    return test_cases