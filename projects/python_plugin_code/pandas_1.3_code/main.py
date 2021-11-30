PACKAGE_NAME = "plugin_code"
COPY_MARKER = "# == copy after here =="


def generate_plugin_code():
    file_names = [
        'apply_args.py',
        'base_apply_patcher.py',
        'apply_fallback_patch.py',
        'apply_map_args.py',
        'base_apply_map_patcher.py',
        'apply_map_fallback_patch.py',
        'background_gradient_patch.py',
        'highlight_extrema_patch.py',
        'highlight_between_patch.py',
        'exported_style.py',
        'table_structure.py',
        'patched_styler.py',
    ]
    with open('generated/plugin_code', 'w') as outfile:
        for file_name in file_names:
            with open(f"{PACKAGE_NAME}/{file_name}") as infile:
                copy_marker_found = False
                for line in infile:
                    stripped_line = line.strip()
                    if copy_marker_found:
                        if not stripped_line.startswith("#"):
                            if stripped_line.startswith("print"):
                                print("WARN: print found in code")
                            if f"from {PACKAGE_NAME}" in stripped_line:
                                # import is not required in the final plugin code
                                print(f"ERROR: wrong placed import in file '{file_name}' - move it above copy marker")
                                return
                            outfile.write(line)
                    else:
                        if stripped_line.startswith(COPY_MARKER):
                            copy_marker_found = True
                            outfile.write("\n")
                        elif not stripped_line.startswith("#"):
                            if any(s in stripped_line for s in ["from", "import"]) and f"{PACKAGE_NAME}" not in stripped_line:
                                # import is required in the final plugin code
                                print(f"ERROR: wrong placed import in file '{file_name}' - move it below copy marker")
                                return
                if not copy_marker_found:
                    print(f"ERROR: specified file '{file_name}' doesn't contain a copy marker '{COPY_MARKER}'")
    print("done")


if __name__ == "__main__":
    generate_plugin_code()
