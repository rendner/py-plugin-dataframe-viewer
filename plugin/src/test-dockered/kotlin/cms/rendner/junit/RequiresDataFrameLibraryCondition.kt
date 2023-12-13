/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.junit

import cms.rendner.TestProperty
import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport

abstract class RequiresDataFrameLibraryCondition(
    private val library: DataFrameLibrary,
    private val annotationType: Class<out Annotation>,
    ) : ExecutionCondition {

    private val enabled: ConditionEvaluationResult = ConditionEvaluationResult.enabled("@${annotationType.simpleName} is not present or present and ${library.moduleName} is available")
    private val disabled: ConditionEvaluationResult = ConditionEvaluationResult.disabled("@${annotationType.simpleName} is present but ${library.moduleName} is not available")

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        AnnotationSupport.findAnnotation(context.element, annotationType).orNull()
            ?: AnnotationSupport.findAnnotation(context.testClass, annotationType).orNull()
            ?: return enabled
        return if (TestProperty.getDataFrameLibraries().contains(library)) enabled else disabled
    }
}

class RequiresPandasCondition : RequiresDataFrameLibraryCondition(DataFrameLibrary.PANDAS, RequiresPandas::class.java)
class RequiresPolarsCondition : RequiresDataFrameLibraryCondition(DataFrameLibrary.POLARS, RequiresPolars::class.java)
