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
import inspect
import typing
from abc import ABC, abstractmethod
from typing import Any, List, Optional, Union

from cms_rendner_sdfv.base.transforms import to_json
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, Region, TableFrame, \
    TableSourceKind, TableStructure


class AbstractTableFrameGenerator(ABC):
    @abstractmethod
    def _region_or_region_of_frame(self, region: Region = None) -> Region:
        pass

    @abstractmethod
    def generate(self,
                 region: Region = None,
                 exclude_row_header: bool = False,
                 exclude_col_header: bool = False,
                 ) -> TableFrame:
        pass

    def generate_by_combining_chunks(self,
                                     rows_per_chunk: int,
                                     cols_per_chunk: int,
                                     region: Region = None,
                                     ) -> TableFrame:
        result = None

        region = self._region_or_region_of_frame(region)

        for chunk_region in region.iterate_chunkwise(rows_per_chunk, cols_per_chunk):

            chunk_contains_elements_of_first_row = chunk_region.first_row == 0
            chunk_contains_row_start_element = chunk_region.first_col == 0

            chunk_table = self.generate(
                region=Region(
                    region.first_row + chunk_region.first_row,
                    region.first_col + chunk_region.first_col,
                    chunk_region.rows,
                    chunk_region.cols,
                ),
                # request headers only once
                exclude_row_header=not chunk_contains_row_start_element,
                exclude_col_header=not chunk_contains_elements_of_first_row,
            )

            if result is None:
                result = chunk_table
            else:
                if chunk_contains_elements_of_first_row:
                    result.column_labels.extend(chunk_table.column_labels)
                if chunk_contains_row_start_element:
                    if result.index_labels is not None:
                        assert chunk_table.index_labels is not None
                        result.index_labels.extend(chunk_table.index_labels)
                    result.cells.extend(chunk_table.cells)
                else:
                    for i, row in enumerate(chunk_table.cells):
                        result.cells[i + chunk_region.first_row].extend(row)

        return result if result is not None else TableFrame(index_labels=[], column_labels=[], legend=None, cells=[])


class AbstractTableSourceContext(ABC):
    def set_sort_criteria(self, sort_by_column_index: Optional[List[int]], sort_ascending: Optional[List[bool]]):
        pass

    def get_org_indices_of_visible_columns(self, part_start: int, max_columns: int) -> List[int]:
        end = min(part_start + max_columns, self.get_region_of_frame().cols)
        return [] if end <= part_start or part_start < 0 else list(range(part_start, end))

    @abstractmethod
    def get_region_of_frame(self) -> Region:
        pass

    @abstractmethod
    def get_table_structure(self, fingerprint: str) -> TableStructure:
        pass

    @abstractmethod
    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        pass


T = typing.TypeVar('T', bound=AbstractTableSourceContext)


class AbstractTableSource(ABC):
    def __init__(self, kind: TableSourceKind, context: T, fingerprint: str):
        self._kind = kind
        self._context = context
        self._fingerprint = fingerprint

    def get_kind(self) -> TableSourceKind:
        return self._kind

    @staticmethod
    def jsonify(data: Any) -> str:
        return to_json(data)

    def get_org_indices_of_visible_columns(self, part_start: int, max_columns: int) -> List[int]:
        return self._context.get_org_indices_of_visible_columns(part_start, max_columns)

    def get_table_structure(self) -> TableStructure:
        return self._context.get_table_structure(self._fingerprint)

    def set_sort_criteria(self,
                          by_column_index: Optional[List[int]] = None,
                          ascending: Optional[List[bool]] = None,
                          ):
        self._context.set_sort_criteria(by_column_index, ascending)

    def compute_chunk_table_frame(self,
                                  first_row: int,
                                  first_col: int,
                                  rows: int,
                                  cols: int,
                                  exclude_row_header: bool = False,
                                  exclude_col_header: bool = False
                                  ) -> TableFrame:
        return self._context.get_table_frame_generator().generate(
            region=Region(first_row, first_col, rows, cols),
            exclude_row_header=exclude_row_header,
            exclude_col_header=exclude_col_header,
        )


class AbstractTableSourceFactory(ABC):
    def create(self,
               data_source: Any,
               create_config: Union[CreateTableSourceConfig, dict] = None,
               ) -> Union[AbstractTableSource, str]:
        try:
            config = create_config

            if isinstance(config, dict):
                config = CreateTableSourceConfig(**config)
            elif config is None:
                config = CreateTableSourceConfig()

            # use globals and locals of previous frame (caller frame)
            caller_globals = {}
            caller_frame = inspect.currentframe().f_back
            if caller_frame:
                caller_globals.update(caller_frame.f_globals)
                caller_globals.update(caller_frame.f_locals)

            table_source = self._create_internal(data_source, config, caller_globals)
            if not isinstance(table_source, AbstractTableSource):
                if isinstance(table_source, CreateTableSourceFailure):
                    return to_json(table_source)
                expected_type = type(AbstractTableSource)
                actual_type = type(table_source)
                raise ValueError(
                    f"Created table_source is of type: {actual_type}, expected: ${expected_type}."
                )

            return table_source
        except Exception as e:
            return to_json(CreateTableSourceFailure(error_kind="EVAL_EXCEPTION", info=repr(e)))

    @abstractmethod
    def _create_internal(self,
                         data_source: Any,
                         config: CreateTableSourceConfig,
                         caller_globals: dict,
                         ) -> Union[AbstractTableSource, CreateTableSourceFailure]:
        pass
