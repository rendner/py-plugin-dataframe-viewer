from pathlib import Path
from tools.dump_helpers import generate_plugin_modules_dump

if __name__ == "__main__":
    generate_plugin_modules_dump(Path(__file__).parent.name)
