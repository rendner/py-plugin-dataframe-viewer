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
package cms.rendner.intellij.dataframe.viewer.python.utils

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class StringifyUtilsTest {

    @Test
    fun pythonBool_shouldReturnTrue() {
        Assertions.assertThat(stringifyBool(true)).isEqualTo("True")
    }

    @Test
    fun pythonBool_shouldReturnFalse() {
        Assertions.assertThat(stringifyBool(false)).isEqualTo("False")
    }

    @Test
    fun pythonString_shouldSurroundValueWithQuotes() {
        Assertions.assertThat(stringifyString("abc")).isEqualTo("'abc'")
    }

    @Test
    fun pythonMethodCall_shouldBuildMethodCallExpr() {
        val call = stringifyMethodCall("a", "hello") {
            stringParam("Python")
            numberParam(2)
            boolParam(true)
            boolParam(false)
        }

        Assertions.assertThat(call).isEqualTo("a.hello('Python', 2, True, False)")
    }
}