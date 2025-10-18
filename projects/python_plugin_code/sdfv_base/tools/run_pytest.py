import subprocess
import sys
from typing import List


def print_green(message: str) -> None:
    print(f"\033[92m\n{message}\033[0m")


def print_red(message: str) -> None:
    print(f"\033[91m\n{message}\033[0m")


def run_pytest(description: str, extra_args: List[str] = None):
    print(f"\nRunning {description}...")
    result = subprocess.run([sys.executable, "-m", "pytest", "-rav"] + (extra_args or []), check=False)

    if result.returncode != 0:
        print_red(f"❌ {description} FAILED with exit code {result.returncode}")
        sys.exit(result.returncode)

    print_green(f"✓ {description} passed")
    return result.returncode
