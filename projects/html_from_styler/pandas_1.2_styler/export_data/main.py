from pathlib import Path
from export_data.utils.export_utils import collect_all_test_cases

export_test_data = collect_all_test_cases(str(Path(__file__).parent))

breakpoint()
