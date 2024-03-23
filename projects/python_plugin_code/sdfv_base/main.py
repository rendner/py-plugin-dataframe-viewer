from tools.copy_file_without_comments import copy_file_without_comments
from tools.generate_plugin_modules_dump import generate_plugin_modules_dump

if __name__ == "__main__":
    generate_plugin_modules_dump()
    copy_file_without_comments(source="importer/plugin_modules_importer.py", target="generated/plugin_modules_importer")
