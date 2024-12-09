/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components.filter

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.python.bridge.ICompletionVariant
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.util.Key

interface IFilterInputChangedListener {
    fun filterInputChanged()
}

interface IDataFrameColumnNameContributor {
    /**
     * Provides completion for column names.
     *
     * @param identifier identifier to reference the DataFrame.
     * @param isSyntheticIdentifier true if the identifier is the synthetic DataFrame identifier.
     *
     * @return list of matching column names.
     */
    @Throws(EvaluateException::class)
    fun getCompletionVariants(identifier: String, isSyntheticIdentifier: Boolean): List<ICompletionVariant>
}

interface IFilterInputCompletionContributor {
    companion object {
        val COMPLETION_CONTRIBUTOR: Key<IFilterInputCompletionContributor?> = Key.create("cms.rendner.COMPLETION_CONTRIBUTOR")
    }

    /**
     * Sets a column name contributor.
     */
    fun setColumnNameContributor(nameContributor: IDataFrameColumnNameContributor?)

    /**
     * Adds filter related completion variants, based on completion parameters.
     */
    fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet)

    /**
     * Returns the type for the synthetic identifier.
     */
    fun getSyntheticIdentifierType(): DataFrameLibrary

    /**
     * Returns true if the synthetic DataFrame identifier is provided by this contributor.
     */
    fun isSyntheticIdentifierEnabled(): Boolean
}

data class FilterInputState(val text: String, val containsSyntheticFrameIdentifier: Boolean = false)