#  Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
import math
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, List, Union, TypeVar, Dict, Callable

from cms_rendner_sdfv.base.exceptions import TableSourceException, TableSourceErrorKind
from cms_rendner_sdfv.base.temp import TEMP_VARS
from cms_rendner_sdfv.base.serialization import serialize_result_or_exception_to_json
from cms_rendner_sdfv.base.types import CreateTableSourceConfig, Region, ChunkDataResponse, \
    TableSourceKind, TableStructure, TableInfo, SortConfig, TableStructureColumn, \
    CompletionVariant, NestedCompletionVariant, ChunkDataRequest, CellMeta, TextAlign, \
    FilterConfig, FilterResult
import cms_rendner_sdfv.base.types as _types


@dataclass
class MinMaxInfo:
    min: Any
    max: Any
    is_inf: bool = field(init=False)

    def __post_init__(self):
        vmin = self.min.real if isinstance(self.min, complex) else self.min
        vmax = self.max.real if isinstance(self.max, complex) else self.max
        try:
            self.is_inf = (vmin is not None and math.isinf(vmin)) or (vmax is not None and math.isinf(vmax))
        except:
            self.is_inf = False


class AbstractMetaComputer:
    def __init__(self):
        self.__min_max_cache: Dict[int, Union[None, MinMaxInfo]] = dict()

    def clear_min_max_cache(self):
        self.__min_max_cache.clear()

    @abstractmethod
    def _compute_min_max_at(self, col: int) -> (Any, Any):
        pass

    def _is_nan(self, v: Any) -> bool:
        return math.isnan(v)

    def __get_min_max_info_at(self, col: int) -> Union[None, MinMaxInfo]:
        if col not in self.__min_max_cache:
            try:
                min, max = self._compute_min_max_at(col)
            except:
                min, max = None, None

            if min is None or max is None:
                self.__min_max_cache[col] = None
            else:
                self.__min_max_cache[col] = MinMaxInfo(min=min, max=max)

        return self.__min_max_cache.get(col)

    def compute_cell_meta(self,
                          col: int,
                          value: Any,
                          css: Union[None, Dict[str, str]] = None,
                          ) -> Union[None, str]:
        info = self.__get_min_max_info_at(col)
        if info is None:
            return None

        if value is None:
            meta = CellMeta(cmap_value=-1)
        else:
            try:
                is_nan = self._is_nan(value)
            except:
                is_nan = False

            if is_nan:
                meta = CellMeta.nan()
            else:
                meta = CellMeta(
                    is_min=value == info.min,
                    is_max=value == info.max,
                    cmap_value=self.__compute_cmap_value(info, value),
                )

        if css is not None:
            meta.background_color = css.get('background-color')
            meta.text_color = css.get('color')
            meta.text_align = TextAlign.from_css(css.get('text-align'))

        return meta.pack()

    @staticmethod
    def __compute_cmap_value(info: MinMaxInfo, value: Any) -> Union[None, int]:
        if info.is_inf:
            return -1
        try:
            if info.min is None or info.max is None:
                return None
            if info.min == info.max:
                return 0
            vmin = info.min
            vmax = info.max
            if isinstance(vmin, complex):
                vmin = vmin.real
            if isinstance(vmax, complex):
                vmax = vmax.real
            if isinstance(value, complex):
                value = value.real
            normalized = (value - vmin) / (vmax - vmin)
            return int(100_000 * normalized)
        except:
            return None


class ChunkDataGenerator(ABC):
    def __init__(self, bounds: Region):
        self.__bounds = bounds

    def _before_generate(self, region: Region):
        pass

    def _after_generate(self, region: Region):
        pass

    def _compute_row_headers(self, region: Region, response: ChunkDataResponse):
        pass

    def _compute_cells(self, region: Region, response: ChunkDataResponse):
        pass

    def generate(self,
                 region: Union[None, Region] = None,
                 request: Union[None, ChunkDataRequest] = None,
                 ) -> ChunkDataResponse:
        if request is None:
            request = ChunkDataRequest()

        region = self.__bounds.get_bounded_region(region)
        response = ChunkDataResponse()

        self._before_generate(region=region)

        if request.with_row_headers:
            self._compute_row_headers(region, response)

        if request.with_cells:
            self._compute_cells(region, response)

        self._after_generate(region=region)

        return response

    def generate_by_combining_chunks(self,
                                     rows_per_chunk: int,
                                     cols_per_chunk: int,
                                     region: Region = None,
                                     ) -> ChunkDataResponse:
        result = None

        if region is None:
            region = self.__bounds

        for local_chunk_region in region.iterate_local_chunkwise(rows_per_chunk, cols_per_chunk):

            chunk_contains_row_start_element = local_chunk_region.first_col == 0

            chunk_data = self.generate(
                region=local_chunk_region.translate(region.first_row, region.first_col),
                # request headers only once
                request=ChunkDataRequest(with_row_headers=chunk_contains_row_start_element),
            )

            assert chunk_data.cells is not None

            if result is None:
                result = chunk_data
            else:
                if chunk_contains_row_start_element:
                    if result.row_headers is not None:
                        assert chunk_data.row_headers is not None
                        result.row_headers.extend(chunk_data.row_headers)
                    result.cells.extend(chunk_data.cells)
                else:
                    for i, row in enumerate(chunk_data.cells):
                        result.cells[i + local_chunk_region.first_row].extend(row)

        return result if result is not None else ChunkDataResponse()


class AbstractTableSourceContext(ABC):
    @abstractmethod
    def unlink(self):
        pass

    def apply_sort(self, config: SortConfig):
        pass

    def get_column_name_completion_variants(self, source: Any, is_synthetic_df: bool) -> List[
        Union[CompletionVariant, NestedCompletionVariant]]:
        pass

    @abstractmethod
    def get_column_statistics(self, col_index: int) -> Dict[str, str]:
        pass

    @abstractmethod
    def get_table_structure(self) -> TableStructure:
        pass

    @abstractmethod
    def get_chunk_data_generator(self) -> ChunkDataGenerator:
        pass

    @abstractmethod
    def apply_filter(self, config: FilterConfig) -> FilterResult:
        pass

    @staticmethod
    def _evaluate_filter_frame(source_frame: Any, config: FilterConfig) -> Any:
        filter_frame = None

        if config.expr is not None and config.expr != "":
            # find first caller outside of plugin code to get the right globals and locals
            caller_globals = {}
            my_module_prefix = "cms_rendner_sdfv"
            frame = inspect.currentframe()
            try:
                while frame is not None:
                    module_name = frame.f_globals.get("__name__", "")
                    if not module_name.startswith(my_module_prefix):
                        caller_globals.update(frame.f_globals)
                        caller_globals.update(frame.f_locals)
                        break
                    frame = frame.f_back
            finally:
                del frame

            if config.synthetic_identifier_used:
                # add required data-frame to make it accessible for eval
                # "_df" is the synthetic identifier which should resolve to the data-frame
                caller_globals["_df"] = source_frame

            try:
                filter_frame = eval(config.expr, caller_globals)
            except Exception as e:
                raise TableSourceException(
                    error_kind=TableSourceErrorKind.EVAL_FAILED,
                    cause=e,
                )

        return filter_frame

    @staticmethod
    def _compute_sort_config_after_filtering(
            sort_before: SortConfig,
            columns_before: List[TableStructureColumn],
            columns_after: List[TableStructureColumn]
    ) -> SortConfig:
        # Computes a new SortCriteria by keeping only columns that exist in column_info_after.
        # Maps column indices from before to after based on matching column IDs.

        if sort_before.is_empty():
            return sort_before

        col_id_to_new_index = {col.id: idx for idx, col in enumerate(columns_after)}

        new_column_idx = []
        new_ascending = []

        for old_idx, asc in zip(sort_before.column_idx, sort_before.ascending):
            col_id = columns_before[old_idx].id
            if col_id in col_id_to_new_index:
                new_column_idx.append(col_id_to_new_index[col_id])
                new_ascending.append(asc)

        if not new_column_idx:
            return SortConfig()

        return SortConfig(column_idx=new_column_idx, ascending=new_ascending)


TSC = TypeVar('TSC', bound=AbstractTableSourceContext)


# All methods of a table source, that return a value (not 'self')
# and are part of the public API must return a serialized value.
# Use the method 'serialize' to serialize a value.
class AbstractTableSource(ABC):
    def __init__(self, kind: TableSourceKind, context: TSC):
        self.__kind = kind
        self._context = context

    def unlink(self):
        self._context.unlink()
        self._context = None

    # DO NOT USE JSON DECORATOR
    def invoke_with_typed_kwargs(self, method_name: str, kwargs_factory: Callable[[Any], Dict[str, Any]]):
        kwargs = kwargs_factory(_types)
        method = getattr(self, method_name)
        return method(**kwargs)

    @serialize_result_or_exception_to_json
    def get_column_name_completion_variants(self, source: Any, is_synthetic_df: bool) -> List[
        Union[CompletionVariant, NestedCompletionVariant]]:
        return self._context.get_column_name_completion_variants(
            source=source,
            is_synthetic_df=is_synthetic_df,
        )

    # DO NOT USE JSON DECORATOR
    def get_info(self) -> TableInfo:
        return TableInfo(
            kind=TableSourceKind(self.__kind).name,
            structure=self._context.get_table_structure(),
        )

    @serialize_result_or_exception_to_json
    def get_column_statistics(self, col_index: int) -> Dict[str, str]:
        return self._context.get_column_statistics(col_index)

    @serialize_result_or_exception_to_json
    def apply_sort(self, config: SortConfig):
        self._context.apply_sort(config)

    @serialize_result_or_exception_to_json
    def apply_filter(self, config: FilterConfig) -> FilterResult:
        return self._context.apply_filter(config)

    @serialize_result_or_exception_to_json
    def compute_chunk_data(self,
                           region: Region,
                           request: Union[None, ChunkDataRequest] = None,
                           ) -> ChunkDataResponse:
        return self._context.get_chunk_data_generator().generate(
            region=region,
            request=request,
        )


class AbstractTableSourceFactory(ABC):

    @serialize_result_or_exception_to_json
    def create(self,
               temp_var_slot_id: str,
               data_source: Any,
               create_config: Union[CreateTableSourceConfig, dict] = None,
               ) -> TableInfo:
        config = create_config

        if isinstance(config, dict):
            config = CreateTableSourceConfig(**config)
        elif config is None:
            config = CreateTableSourceConfig()

        table_source = self._create_internal(data_source, config)

        if not isinstance(table_source, AbstractTableSource):
            expected_type = type(AbstractTableSource)
            actual_type = type(table_source)
            raise TableSourceException(
                error_kind=TableSourceErrorKind.UNSUPPORTED_TABLE_SOURCE_TYPE,
                info=f"Created table_source is of type: {actual_type}, expected: ${expected_type}."
            )

        TEMP_VARS[temp_var_slot_id] = table_source

        return table_source.get_info()

    @abstractmethod
    def _create_internal(self,
                         data_source: Any,
                         config: CreateTableSourceConfig,
                         ) -> AbstractTableSource:
        pass
