#!/usr/bin/env python3
import os
import sys
import subprocess
from pathlib import Path
from typing import Union

# ANSI color codes
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
BOLD = '\033[1m'
NC = '\033[0m'  # No Color

def run_tests_of_subdirectory(project_path: Path) -> tuple[bool, Union[None, str]]:
    test_script = project_path / "run_tests.py"
    pytest_ini = project_path / "pytest.ini"

    test_runner = None

    try:
        if test_script.exists():
            test_runner = "run_tests.py"
            subprocess.run(
                ["pipenv", "run", "python", "run_tests.py"],
                cwd=project_path,
                check=True
            )
        elif pytest_ini.exists():
            test_runner = "pytest"
            subprocess.run(
                ["pipenv", "run", "pytest", "-rav"],
                cwd=project_path,
                check=True
            )
        else:
            return False, test_runner

        return True, test_runner

    except subprocess.CalledProcessError:
        return False, test_runner
    except Exception as e:
        print(f"{RED}ERROR: {e}{NC}")
        return False, test_runner

def run_tests():
    projects_dir = Path("/usr/src/app/projects")

    if not projects_dir.exists():
        print(f"{RED}Projects directory not found: {projects_dir}{NC}")
        sys.exit(1)

    subdirectories = sorted([p for p in projects_dir.iterdir() if p.is_dir()])

    if not subdirectories:
        print(f"{YELLOW}No subdirectories found in {projects_dir}{NC}")
        sys.exit(0)

    results = []

    for subdirectory_path in subdirectories:
        success, test_runner = run_tests_of_subdirectory(subdirectory_path)
        if test_runner is not None:
            results.append({
                'name': subdirectory_path.name,
                'success': success,
                'test_runner': test_runner
            })

    # Final summary
    print(f"\n{BOLD}{BLUE}TEST SUMMARY{NC}")
    print(f"{BLUE}{'=' * 80}{NC}\n")

    results_count = len(results)
    passed_count = sum(1 for r in results if r['success'])
    failed_count = results_count - passed_count

    for index, result in enumerate(results, start=1):
        status = f"{GREEN}✓{NC}" if result['success'] else f"{RED}✗{NC}"
        print(f"{status} [{index}/{results_count}] {result['name']} (test_runner: {result['test_runner']})")

    print()

    if failed_count == 0:
        print(f"{GREEN}{BOLD}All tests passed! ({passed_count}/{results_count}){NC}\n")
        sys.exit(0)
    else:
        print(f"{RED}{BOLD}Failed tests: {failed_count}/{results_count}{NC}\n")
        sys.exit(1)

if __name__ == "__main__":
    run_tests()