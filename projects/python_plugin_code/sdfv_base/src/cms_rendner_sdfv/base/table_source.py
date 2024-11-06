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
import inspect
from abc import ABC, abstractmethod
from typing import Any, List, Union, TypeVar

from cms_rendner_sdfv.base.temp import TEMP_VARS, EvaluatedVarsCleaner
from cms_rendner_sdfv.base.transforms import to_json
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, CreateTableSourceFailure, Region, TableFrame, \
    TableSourceKind, TableStructure, CreateTableSourceErrorKind


class AbstractVisibleFrame(ABC):
    def __init__(self, region: Region):
        self.region = region

    @abstractmethod
    def unlink(self):
        pass

    def get_column_indices(self) -> List[int]:
        # default implementation (has to be overwritten in case columns are excluded or reordered)
        return list(range(self.region.cols))


VF = TypeVar('VF', bound=AbstractVisibleFrame)


class AbstractTableFrameGenerator(ABC):
    def __init__(self, visible_frame: VF):
        self._visible_frame: VF = visible_frame

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

        if region is None:
            region = self._visible_frame.region

        for local_chunk in region.iterate_local_chunkwise(rows_per_chunk, cols_per_chunk):

            chunk_contains_elements_of_first_row = local_chunk.first_row == 0
            chunk_contains_row_start_element = local_chunk.first_col == 0

            chunk_table = self.generate(
                region=local_chunk.translate(region.first_row, region.first_col),
                # request headers only once
                exclude_row_header=not chunk_contains_row_start_element,
                exclude_col_header=not chunk_contains_elements_of_first_row,
            )

            if result is None:
                result = chunk_table
            else:
                if chunk_contains_elements_of_first_row:
                    result.columns.extend(chunk_table.columns)
                if chunk_contains_row_start_element:
                    if result.index_labels is not None:
                        assert chunk_table.index_labels is not None
                        result.index_labels.extend(chunk_table.index_labels)
                    result.cells.extend(chunk_table.cells)
                else:
                    for i, row in enumerate(chunk_table.cells):
                        result.cells[i + local_chunk.first_row].extend(row)

        return result if result is not None else TableFrame(index_labels=[], columns=[], legend=None, cells=[])


class AbstractColumnNameCompleter(ABC):
    def get_variants(self,
                     source: Any,
                     is_synthetic_df: bool,
                     name_to_complete: Union[None, str, int],
                     max_matches: int = 200,
                     ) -> List[str]:
        matches: List[str] = []
        column_names = self._resolve_column_names(source, is_synthetic_df)

        def add_variant_formatted(v: Union[str, int]) -> int:
            matches.append(f'"{v}"' if isinstance(v, str) else str(v))
            return len(matches)

        if name_to_complete is None:
            for cn in column_names:
                if not isinstance(cn, (int, str)):
                    continue
                if add_variant_formatted(cn) >= max_matches:
                    break
        else:
            prefix = str(name_to_complete).lower()
            variant_type = type(name_to_complete)

            for cn in column_names:
                if not isinstance(cn, variant_type):
                    continue
                if not prefix or str(cn).lower().startswith(prefix):
                    if add_variant_formatted(cn) >= max_matches:
                        break

        return matches

    @abstractmethod
    def _resolve_column_names(self, source: Any, is_synthetic_df: bool) -> List[Any]:
        pass


class AbstractTableSourceContext(ABC):
    @abstractmethod
    def unlink(self):
        pass

    def set_sort_criteria(self, sort_by_column_index: Union[None, List[int]], sort_ascending: Union[None, List[bool]]):
        pass

    def get_column_name_completer(self) -> Union[None, AbstractColumnNameCompleter]:
        return None

    @property
    @abstractmethod
    def visible_frame(self) -> AbstractVisibleFrame:
        pass

    @abstractmethod
    def get_table_structure(self, fingerprint: str) -> TableStructure:
        pass

    @abstractmethod
    def get_table_frame_generator(self) -> AbstractTableFrameGenerator:
        pass


T = TypeVar('T', bound=AbstractTableSourceContext)


class AbstractTableSource(ABC):
    def __init__(self, kind: TableSourceKind, context: T, fingerprint: str):
        self.__kind = kind
        self._context = context
        self.__fingerprint = fingerprint

    def unlink(self):
        self._context.unlink()
        self._context = None

    def get_kind(self) -> TableSourceKind:
        return self.__kind

    def get_column_name_variants(self,
                                 source: Any,
                                 is_synthetic_df: bool,
                                 name_to_complete: Union[str, int],
                                 ) -> List[str]:
        completer = self._context.get_column_name_completer()
        return [] if completer is None else completer.get_variants(
            source=source,
            is_synthetic_df=is_synthetic_df,
            name_to_complete=name_to_complete,
        )

    @staticmethod
    def jsonify(data: Any) -> str:
        return to_json(data)

    def get_table_structure(self) -> TableStructure:
        return self._context.get_table_structure(self.__fingerprint)

    def set_sort_criteria(self,
                          by_column_index: Union[None, List[int]] = None,
                          ascending: Union[None, List[bool]] = None,
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

    def clear(self, id_names: List[str]):
        EvaluatedVarsCleaner.clear(id_names, 1)
        return self


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

            if config.temp_var_slot_id is not None:
                TEMP_VARS[config.temp_var_slot_id] = table_source

            return table_source
        except Exception as e:
            return to_json(
                CreateTableSourceFailure(
                    error_kind=CreateTableSourceErrorKind.EVAL_EXCEPTION,
                    info=repr(e),
                ),
            )

    @abstractmethod
    def _create_internal(self,
                         data_source: Any,
                         config: CreateTableSourceConfig,
                         caller_globals: dict,
                         ) -> Union[AbstractTableSource, CreateTableSourceFailure]:
        pass
