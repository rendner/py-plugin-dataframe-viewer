#  Copyright 2021-2023 cms.rendner (Daniel Schmidt)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import json
import os
from contextlib import closing
from io import StringIO
from pathlib import Path


class PackageStructureDumper:

    @staticmethod
    def to_json(root: str, package: str):
        v_file_system = {}
        root_path = Path(root)
        path_list = (root_path / package).rglob("*.py")
        for path in path_list:
            PackageStructureDumper._insert_with_path_structure(v_file_system, root_path, path)
        if not v_file_system:
            raise ValueError(f"No files to dump, package '{(root_path / package).absolute()}' doesn't contain .py files.")
        return json.dumps(v_file_system, sort_keys=True, indent=4)

    @staticmethod
    def _insert_with_path_structure(virtual_file_system: dict, root_dir: Path, file_path: Path):
        if os.path.getsize(file_path) == 0:
            return

        parts = file_path.relative_to(root_dir).parts
        target_package = virtual_file_system

        for p in parts[:-1]:
            if target_package.get(p, None) is None:
                target_package[p] = {}

            target_package = target_package[p]

        with closing(StringIO()) as output:
            with open(file_path, encoding='utf8', newline='\n') as file:
                for line in file:
                    stripped_line = line.lstrip()
                    if not stripped_line.startswith("#"):
                        output.write(line)

            key = parts[-1][:-len(".py")]
            target_package[key] = output.getvalue()


def generate_plugin_modules_dump(
        src_root: str = "src",
        root_package_to_dump: str = "cms_rendner_sdfv",
        output_file: str = "generated/plugin_modules_dump.json",
):
    dump = PackageStructureDumper.to_json(src_root, root_package_to_dump)
    with open(output_file, 'w', encoding="utf8", newline='\n') as outfile:
        outfile.write(dump)
