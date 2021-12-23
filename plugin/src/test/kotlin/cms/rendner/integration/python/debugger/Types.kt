/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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
data class EvaluateRequest(
    val expression: String,
    val execute: Boolean,
    val trimResult: Boolean,
)

data class EvaluateResponse(
    // the evaluated result
    val value: String? = null,
    // the type of the evaluated value
    val type: String? = null,
    val typeQualifier: String? = null,
    // true if an error occurred during evaluation
    val isError: Boolean = false,
    // a unique id to refer to the evaluated var on python side
    val refId: String? = null,
)

enum class PipenvEnvironment(val label: String) {
    PANDAS_1_1("pandas_1.1"),
    PANDAS_1_2("pandas_1.2"),
    PANDAS_1_3("pandas_1.3");

    companion object {
        fun labelOf(label: String): PipenvEnvironment {
            for (v in values()) {
                if (v.label == label) {
                    return v
                }
            }
            throw IllegalArgumentException("There is no value which matches the label $label")
        }
    }
}