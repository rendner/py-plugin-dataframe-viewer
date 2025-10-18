#!/usr/bin/env python3
import os
import sys
import subprocess
from pathlib import Path

# ANSI color codes
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
BOLD = '\033[1m'
NC = '\033[0m'  # No Color

def run_generate_dump(project_path: Path) -> bool:
    try:
        subprocess.run(
            ["pipenv", "run", "python", "generate_dump.py"],
            cwd=project_path,
            check=True
        )
        return True

    except subprocess.CalledProcessError as e:
        print(f"{RED}✗ Failed to generate dump for {project_path.name} - {e}{NC}")
        return False
    except Exception as e:
        print(f"{RED}ERROR: {e}{NC}")
        return False

def generate_dumps():
    projects_dir = Path("/usr/src/app/projects")

    if not projects_dir.exists():
        print(f"{RED}Projects directory not found: {projects_dir}{NC}")
        sys.exit(1)

    projects_to_dump = []
    for item in projects_dir.iterdir():
        if item.is_dir() and (item / "generate_dump.py").exists():
            projects_to_dump.append(item)

    projects_to_dump.sort()

    if not projects_to_dump:
        print(f"{YELLOW}No projects to dump found in {projects_dir}{NC}")
        sys.exit(0)

    results = []

    for project_path in projects_to_dump:
        success = run_generate_dump(project_path)

        results.append({
            'name': project_path.name,
            'path': str(project_path.relative_to(projects_dir)),
            'success': success
        })

    # Final summary
    print(f"\n{BOLD}{BLUE}GENERATION SUMMARY{NC}")
    print(f"{BLUE}{'=' * 80}{NC}\n")

    results_count = len(results)
    passed_count = sum(1 for r in results if r['success'])
    failed_count = results_count - passed_count

    for index, result in enumerate(results, start=1):
        status = f"{GREEN}✓{NC}" if result['success'] else f"{RED}✗{NC}"
        print(f"{status} [{index}/{results_count}] {result['path']}")

    print()

    if failed_count == 0:
        print(f"{GREEN}{BOLD}All dumps generated successfully! ({passed_count}/{results_count}){NC}\n")
        sys.exit(0)
    else:
        print(f"{RED}{BOLD}Failed dumps: {failed_count}/{results_count}{NC}\n")
        sys.exit(1)

if __name__ == "__main__":
    generate_dumps()
