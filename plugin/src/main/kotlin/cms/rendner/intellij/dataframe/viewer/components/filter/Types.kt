/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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

interface IFilterEvalExprBuilder {
    /**
     * Returns the resolved filter eval expression.
     *
     * @param dataFrameRefExpr the expression which should be used instead of the synthetic identifier.
     * If null is specified the unmodified filter expression is returned.
     */
    fun build(dataFrameRefExpr: String?): String
}