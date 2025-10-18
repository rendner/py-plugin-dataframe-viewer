from pathlib import Path
from tools.dump_helpers import generate_plugin_modules_dump

if __name__ == "__main__":
    project_name = Path(__file__).parent.name
    generate_plugin_modules_dump(project_name)
