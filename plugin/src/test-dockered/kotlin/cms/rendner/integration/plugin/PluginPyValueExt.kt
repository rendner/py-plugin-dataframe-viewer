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
package cms.rendner.integration.plugin

import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceCodeProviderRegistry
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr

fun PluginPyValue.toValueEvalExpr(): PyDebugValueEvalExpr {
    return PyDebugValueEvalExpr(refExpr, refExpr, qualifiedType)
}

fun PluginPyValue.toDataSourceInfo(): DataSourceInfo {
    val evalExpr = this.toValueEvalExpr()
    return TableSourceCodeProviderRegistry
        .getApplicableProvider(evalExpr.qualifiedType!!)!!
        .createSourceInfo(evalExpr, this.evaluator)
}