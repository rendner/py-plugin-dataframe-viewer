/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.CreateTableSourceException
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceFactoryImport
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
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

/**
 * Creates [IPyTableSourceRef] instances to enable "communication" with pandas objects.
 */
class TableSourceFactory {

    companion object {
        fun getTempVarsDictRef() = tempVarsDictRef

        fun create(
            evaluator: IPluginPyValueEvaluator,
            tableSourceFactoryImport: TableSourceFactoryImport,
            dataSourceRefExpr: String,
            config: CreateTableSourceConfig? = null,
        ): IPyTableSourceRef {
            val constructorRef =
                stringifyImportWithObjectRef(tableSourceFactoryImport.packageName, tableSourceFactoryImport.className)

            val tableSource = evaluator.evaluate(
                stringifyMethodCall("$constructorRef()", "create") {
                    refParam(dataSourceRefExpr)
                    if (config == null) noneParam() else refParam(json.encodeToString(config))
                }
            )

            if (tableSource.qualifiedType == "builtins.str") {
                val info: CreateTableSourceFailure =
                    json.decodeFromString(tableSource.forcedValue)
                throw CreateTableSourceException(info)
            }

            val info = json.decodeFromString<TableInfo>(
                evaluator
                    .evaluate("${tableSource.refExpr}.get_info()")
                    .forcedValue
            )

            return when (info.kind) {
                "TABLE_SOURCE" -> PyTableSourceRef(tableSource, info.structure, config?.tempVarSlotId)
                "PATCHED_STYLER" -> PyPatchedStylerRef(tableSource, info.structure, config?.tempVarSlotId)
                else -> throw CreateTableSourceException(
                    CreateTableSourceFailure(
                        CreateTableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE,
                        "Unsupported table source type '${tableSource.qualifiedType}' (kind:${info.kind})",
                    )
                )
            }
        }
    }

    private open class PyTableSourceRef(
        pythonValue: PluginPyValue,
        override val tableStructure: TableStructure,
        protected val tempVarSlotId: String?,
        ) : IPyTableSourceRef, TestOnlyIPyTableSourceRefApi {
        protected val refExpr: String = if (tempVarSlotId != null) "$tempVarsDictRef['${tempVarSlotId}']" else pythonValue.refExpr
        protected val evaluator: IPluginPyValueEvaluator = pythonValue.evaluator
        protected val idsToClean: MutableList<String> = mutableListOf()
        protected val typeConverter = PyPluginBaseTypesConverter()

        @Volatile
        private var myCompletionVariants: List<ICompletionVariant>? = null

        override fun testOnly_getRefExpr() = refExpr

        override fun dispose() {
            try {
                // A cleaner is used to ensure the cleanup.
                // The underlying tableSource could be gone (stack frame already dropped) and the cleanup would fail.
                idsToClean.add(tempVarSlotId ?: refExpr)
                val ids = convertCurrentCleanableIdsToPythonListString()
                val methodRef = stringifyImportWithObjectRef("cms_rendner_sdfv.base.temp", "EvaluatedVarsCleaner.clear")
                evaluator.execute("$methodRef(${ids})")
            } catch (ignore: Throwable) {}
        }

        override fun evaluateColumnStatistics(colIndex: Int): Map<String, String> {
            return callOnPythonSide(
                createCallBuilder().withCall("get_column_statistics") {
                    numberParam(colIndex)
                }
            )
        }

        override fun evaluateComputeChunkData(
            chunkRegion: ChunkRegion,
            dataRequest: ChunkDataRequest,
            newSorting: SortCriteria?,
        ): ChunkData {
            return callOnPythonSide(
                createCallBuilder().apply {
                    if (newSorting != null) {
                        withSetSortCriteria(this, newSorting)
                    }
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

        protected fun invokeWithTypedKwargs(callBuilder: PythonChainedCallsBuilder, methodName: String, kwargsMap: Map<String, String>) {
            callBuilder.withCall("invoke_with_typed_kwargs") {
                stringParam(methodName)
                refParam("lambda t: ${stringifyDictionary(kwargsMap.mapValues { "t.${it.value}" })}")
            }
        }

        override fun evaluateGetColumnNameCompletionVariants(
            identifier: String,
            isSyntheticIdentifier: Boolean
        ): List<ICompletionVariant> {
            val itsMe = isSyntheticIdentifier || identifier == refExpr
            if (itsMe) {
                myCompletionVariants?.let { return it }
            }

            val variants: List<ICompletionVariant> = try {
                callOnPythonSide(
                    createCallBuilder().withCall("get_column_name_completion_variants") {
                        if (isSyntheticIdentifier) noneParam() else refParam(identifier)
                        boolParam(isSyntheticIdentifier)
                    }
                )
            } catch (e: EvaluateException) {
                if (e.pythonErrorQName == PythonQualifiedTypes.NameError) {
                    // If "identifier" is a non-existing variable name Python raises a "NameError".
                    // In that case the error is swallowed and an empty list returned.
                    emptyList()
                } else throw e
            }

            if (itsMe) {
                myCompletionVariants = variants
            }

            return variants
        }

        protected inline fun <reified T> callOnPythonSide(builder: PythonChainedCallsBuilder): T {
            val expr = builder.toString()
            return evaluator.evaluate(expr).let {
                // PyCharms "Python Console" doesn't assign temp-var identifier to refer to an evaluated result.
                // The "refExpr" is in that case equals the evaluated expression.
                if (it.refExpr != expr) {
                    idsToClean.add(it.refExpr)
                }
                // A Python table source returns its values serialized as a JSON string.
                // Therefore, the data has to be deserialized into the expected type.
                json.decodeFromString(it.forcedValue)
            }
        }

        protected fun createCallBuilder(): PythonChainedCallsBuilder {
            return PythonChainedCallsBuilder(refExpr).apply {
                this.withCall("clear") { refParam(convertCurrentCleanableIdsToPythonListString()) }
            }
        }

        protected fun convertCurrentCleanableIdsToPythonListString(): String {
            val ids = idsToClean.joinToString(prefix = "[", postfix = "]") { stringifyString(it) }
            idsToClean.clear()
            return ids
        }

        protected fun withSetSortCriteria(builder: PythonChainedCallsBuilder, sortCriteria: SortCriteria) {
            builder.withCall("set_sort_criteria") {
                sortCriteria.byIndex.let {
                    if (it.isEmpty()) noneParam() else refParam(it.toString())
                }
                sortCriteria.ascending.let {
                    if (it.isEmpty()) noneParam() else refParam(it.map { e -> stringifyBool(e) }.toString())
                }
            }
        }
    }

    private class PyPatchedStylerRef(
        pythonValue: PluginPyValue,
        tableStructure: TableStructure,
        tempVarSlotId: String?
    ) : PyTableSourceRef(pythonValue, tableStructure, tempVarSlotId), IPyPatchedStylerRef {

        override fun evaluateValidateAndComputeChunkData(
            chunkRegion: ChunkRegion,
            dataRequest: ChunkDataRequest,
            newSorting: SortCriteria?,
        ): ValidatedChunkData {
            return callOnPythonSide(
                createCallBuilder().apply {
                    if (newSorting != null) {
                        withSetSortCriteria(this, newSorting)
                    }

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
}