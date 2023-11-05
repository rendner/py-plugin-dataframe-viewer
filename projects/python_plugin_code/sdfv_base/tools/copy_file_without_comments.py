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
def copy_file_without_comments(source: str, target: str):
    with open(target, 'w', encoding='utf8', newline='\n') as outfile:
        with open(source, encoding='utf8', newline='\n') as file:
            for line in file:
                stripped_line = line.lstrip()
                if not stripped_line.startswith("#"):
                    outfile.write(line)
