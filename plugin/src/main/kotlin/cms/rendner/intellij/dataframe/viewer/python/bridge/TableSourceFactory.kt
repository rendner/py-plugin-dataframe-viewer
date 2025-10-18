/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.TableSourceException
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.utils.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        serializersModule = bridgeSerializersModule
    }
}

private val tempVarsDictRef = stringifyImportWithObjectRef("cms_rendner_sdfv.base.temp", "TEMP_VARS")
private val cleanerRef = "$tempVarsDictRef['_cleaner_']"

private var tempVarIdCounter: Int = 0

private inline fun <reified T> callAndDeserializeResult(
    frameAccessor: IPluginPyFrameAccessor,
    expr: String,
    ): T {
    frameAccessor.evaluate(expr).let { value ->
        // Python table source and factory return values serialized as JSON string.
        if (value.type == "ResultJsonString") {
            return json.decodeFromString<T>(value.forcedValue)
        } else if (value.type == "ExceptionJsonString") {
            throw TableSourceException(
                json.decodeFromString<TableSourceExceptionData>(value.forcedValue),
            )
        } else if (T::class == Unit::class && value.qualifiedType == PythonQualifiedTypes.NONE) {
            return Unit as T
        }
        throw TableSourceException(
            TableSourceExceptionData(
                TableSourceErrorKind.UNKNOWN_ERROR,
                "Unexpected value type '${value.typeQualifier}.${value.type}' when deserializing JSON string.",
            ),
        )
    }
}

/**
 * Creates [IPyTableSourceRef] instances to enable "communication" with DataFrame objects.
 */
class TableSourceFactory {

    companion object {
        fun getTempVarsDictRef() = tempVarsDictRef

        fun create(
            frameAccessor: IPluginPyFrameAccessor,
            dataSourceInfo: DataSourceInfo,
            config: CreateTableSourceConfig? = null,
        ): IPyTableSourceRef {
            val slotId = "_temp_slot_id_${tempVarIdCounter++}"

            val constructorRef = dataSourceInfo.tableSourceFactoryImport.let { import ->
                stringifyImportWithObjectRef(import.packageName, import.className)
            }

            val tableInfo = callAndDeserializeResult<TableInfo>(
                frameAccessor,
                stringifyMethodCall("$constructorRef()", "create") {
                    stringParam(slotId)
                    refParam(dataSourceInfo.source.refExpr)
                    if (config == null) noneParam() else refParam(json.encodeToString(config))
                }
            )

            val ctx = TableSourceContext(frameAccessor, dataSourceInfo)

            return when (tableInfo.kind) {
                "TABLE_SOURCE" -> PyTableSourceRef(ctx, tableInfo.structure, slotId)
                "PATCHED_STYLER" -> PyPatchedStylerRef(ctx, tableInfo.structure, slotId)
                else -> throw TableSourceException(
                    TableSourceExceptionData(
                        TableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
                        "Unsupported table source kind: ${tableInfo.kind}",
                    )
                )
            }
        }
    }

    private open class PyTableSourceRef(
        override val context: TableSourceContext,
        override val initialTableStructure: TableStructure,
        private val slotId: String,
        ) : IPyTableSourceRef, TestOnlyIPyTableSourceRefApi {
        protected val typeConverter = PyPluginBaseTypesConverter()

        override fun testOnly_getRefExpr(): String = "$tempVarsDictRef['$slotId']"

        @Volatile
        private var disposed: Boolean = false

        override fun disposeTableSource() {
            if (disposed) return

            disposed = true
            try {
                context.frameAccessor.execute("${cleanerRef}.free_slots(['${slotId}'])")
            } catch (_: Exception) {
                // When debug session is not paused, disposing fails and the resource is not freed.
                // I expect this case will not happen often. Solution could be to collect these slotIds
                // and try to free them again as soon as the session is paused again.
            }
        }

        override fun isAlive(): Boolean {
            return !disposed && context.frameAccessor.isAlive()
        }

        override fun getColumnStatistics(colIndex: Int): Map<String, String> {
            return callAndDeserializeResult(
                context.frameAccessor,
                tableSourceCallBuilder {
                    withCall("get_column_statistics") {
                        numberParam(colIndex)
                    }
                }
            )
        }

        override fun computeChunkData(
            chunkRegion: ChunkRegion,
            dataRequest: ChunkDataRequest,
        ): ChunkData {
            return callAndDeserializeResult(
                context.frameAccessor,
                tableSourceCallBuilder {
                    invokeWithTypedKwargs(
                        this,
                        "compute_chunk_data",
                        mapOf(
                            "region" to typeConverter.toRegion(chunkRegion),
                            "request" to typeConverter.toChunkDataRequest(dataRequest),
                        )
                    )
                }
            )
        }

        protected fun invokeWithTypedKwargs(tableSourceCallBuilder: PythonChainedCallsBuilder, methodName: String, kwargsMap: Map<String, String>) {
            tableSourceCallBuilder.withCall("invoke_with_typed_kwargs") {
                stringParam(methodName)
                refParam("lambda t: ${stringifyDictionary(kwargsMap.mapValues { "t.${it.value}" })}")
            }
        }

        override fun getColumnNameCompletionVariants(
            identifier: String,
            isSyntheticIdentifier: Boolean
        ): List<ICompletionVariant> {
            val variants: List<ICompletionVariant> = try {
                callAndDeserializeResult(
                    context.frameAccessor,
                    tableSourceCallBuilder {
                        withCall("get_column_name_completion_variants") {
                            if (isSyntheticIdentifier) noneParam() else refParam(identifier)
                            boolParam(isSyntheticIdentifier)
                        }
                    }
                )
            } catch (e: EvaluateException) {
                if (e.pythonErrorQName == PythonQualifiedTypes.NameError) {
                    // If "identifier" is a non-existing variable name Python raises a "NameError".
                    // In that case the error is swallowed and an empty list returned.
                    emptyList()
                } else throw e
            }

            return variants
        }

        override fun applyFilter(config: FilterConfig): FilterResult {
            return callAndDeserializeResult(
                context.frameAccessor,
                tableSourceCallBuilder {
                    invokeWithTypedKwargs(
                        this,
                        "apply_filter",
                        mapOf(
                            "config" to typeConverter.toFilterConfig(config),
                        )
                    )
                }
            )
        }

        override fun applySort(config: SortConfig) {
            callAndDeserializeResult<Unit>(
                context.frameAccessor,
                tableSourceCallBuilder {
                    invokeWithTypedKwargs(
                        this,
                        "apply_sort",
                        mapOf(
                            "config" to typeConverter.toSortConfig(config),
                        )
                    )
                }
            )
        }

        protected fun tableSourceCallBuilder(init: PythonChainedCallsBuilder.() -> Unit): String {
            return PythonChainedCallsBuilder("$tempVarsDictRef['$slotId']").apply { init() }.toString()
        }
    }

    private class PyPatchedStylerRef(
        context: TableSourceContext,
        initialTableStructure: TableStructure,
        slotId: String,
    ) : PyTableSourceRef(context, initialTableStructure, slotId), IPyPatchedStylerRef {

        override fun validateAndComputeChunkData(
            chunkRegion: ChunkRegion,
            dataRequest: ChunkDataRequest,
        ): ValidatedChunkData {
            return callAndDeserializeResult(
                context.frameAccessor,
                tableSourceCallBuilder {
                    invokeWithTypedKwargs(
                        this,
                        "validate_and_compute_chunk_data",
                        mapOf(
                            "region" to typeConverter.toRegion(chunkRegion),
                            "request" to typeConverter.toChunkDataRequest(dataRequest),
                        )
                    )
                }
            )
        }
    }
}

private class PyPluginBaseTypesConverter {
    fun toChunkDataRequest(request: ChunkDataRequest): String {
        val paramWithRowHeaders = "with_row_headers=${stringifyBool(request.withRowHeaders)}"
        val paramWithCells= "with_cells=${stringifyBool(request.withCells)}"
       return "ChunkDataRequest($paramWithCells, $paramWithRowHeaders)"
    }

    fun toRegion(region: ChunkRegion): String {
        return "Region(${region.firstRow}, ${region.firstColumn}, ${region.numberOfRows}, ${region.numberOfColumns})"
    }

    fun toFilterConfig(config: FilterConfig): String {
        // filter-expr can contain strings therefore " and ' have correctly escaped
        val expr = if (config.expr == null) "None" else json.encodeToString(config.expr)
        val syntheticIdentifierUsed = stringifyBool(config.syntheticIdentifierUsed)
        return "FilterConfig(expr=${expr}, synthetic_identifier_used=${syntheticIdentifierUsed})"
    }

    fun toSortConfig(config: SortConfig): String {
        val columns = if (config.byIndex.isEmpty()) "None" else config.byIndex.toString()
        val ascending = if (config.ascending.isEmpty()) "None" else config.ascending.map { e -> stringifyBool(e) }.toString()
        return "SortConfig(column_idx=${columns}, ascending=${ascending})"
    }
}
