#  Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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


def truncate_str(s: str, max_length: int) -> str:
    return s if len(s) <= max_length else s[:max_length - 1] + '…'


def fq_type(o) -> str:
    klass = getattr(o, '__class__', '')
    module = getattr(klass, '__module__', '')
    qname = getattr(klass, '__qualname__', '')
    return f'{module}.{qname}'
