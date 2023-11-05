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
def inject_plugin_modules_importer(root_package_name: str):
    import sys
    from importlib.abc import MetaPathFinder, Loader
    from importlib import util
    from typing import Optional, Union, List

    class SDFVPluginModulesImporter(MetaPathFinder, Loader):
        def find_spec(self, fullname, path, target=None):
            spec = None
            if fullname.startswith(root_package_name):
                if fullname == root_package_name:
                    # the root namespace
                    spec = util.spec_from_loader(fullname, None, is_package=True)
                elif fullname == f"{root_package_name}.package_registry":
                    spec = util.spec_from_loader(fullname, self, is_package=False)
                else:
                    package_registry = sys.modules.get(f"{root_package_name}.package_registry", None)
                    if package_registry is not None:
                        spec = package_registry.get_module_spec_for_entry(fullname)
            return spec

        def exec_module(self, module):
            packages: dict = {}
            packaged_dump_ids = []

            def _add_non_existing_entries(target: dict, update: dict):
                for key in update:
                    if key not in target:
                        target[key] = update[key]
                    elif isinstance(target[key], dict) and isinstance(update[key], dict):
                        _add_non_existing_entries(target[key], update[key])

            def _get_package_entry(fq_name: str) -> Union[dict, str, None]:
                parts = fq_name.split('.')
                parent_package = packages
                for p in parts[:-1]:
                    parent_package = parent_package.get(p, None)
                    if parent_package is None or not isinstance(parent_package, dict):
                        return None
                return parent_package.get(parts[-1], None)

            def register_package_dump(dumb_id: str, package_dumb: Union[str, dict]):
                if dumb_id not in packaged_dump_ids:
                    if isinstance(package_dumb, str):
                        import json
                        package_dumb = json.loads(package_dumb)
                    packaged_dump_ids.append(dumb_id)
                    _add_non_existing_entries(packages, package_dumb)

            def _get_file_content(fq_name: str) -> Optional[str]:
                entry = _get_package_entry(fq_name)
                return entry if isinstance(entry, str) else None

            def get_registered_dump_ids() -> List[str]:
                return list(packaged_dump_ids)

            class _MyVirtualPackageFileLoader(Loader):
                def exec_module(self, module):
                    name = module.__name__
                    code = _get_file_content(name)
                    if code is None:
                        raise ImportError(f'cannot load module {name}, no content', name=name)
                    exec(code, module.__dict__)

            def get_module_spec_for_entry(fq_name: str):
                entry = _get_package_entry(fq_name)
                if entry is None:
                    return None
                from importlib import util
                if isinstance(entry, dict):
                    # namespace package
                    return util.spec_from_loader(fq_name, None, is_package=True)
                elif isinstance(entry, str):
                    return util.spec_from_loader(fq_name, _MyVirtualPackageFileLoader(), is_package=False)
                return None

            module.__dict__['register_package_dump'] = register_package_dump
            module.__dict__['get_registered_dump_ids'] = get_registered_dump_ids
            module.__dict__['get_module_spec_for_entry'] = get_module_spec_for_entry

    sys.meta_path.append(SDFVPluginModulesImporter())


inject_plugin_modules_importer("cms_rendner_sdfv")
del inject_plugin_modules_importer
