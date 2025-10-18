from pathlib import Path
from tools.dump_helpers import generate_plugin_modules_dump

if __name__ == "__main__":
    # Python < 3.9: __file__ returns a relative path
    project_name = Path(__file__).resolve().parent.name
    generate_plugin_modules_dump(project_name)
