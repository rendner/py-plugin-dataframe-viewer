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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PythonCodeProviderFactoryTest {

    @Test
    fun shouldReturnCodeForPandas_1_1() {
        val codeProvider = PythonCodeProviderFactory.createProviderFor(PandasVersion.fromString("1.1.x"))

        assertThat(codeProvider).isNotNull
        codeProvider?.let {
            assertThat(it.version.major).isEqualTo(1)
            assertThat(it.version.minor).isEqualTo(1)
        }
    }

    @Test
    fun shouldReturnCodeForPandas_1_2() {
        val codeProvider = PythonCodeProviderFactory.createProviderFor(PandasVersion.fromString("1.2.x"))

        assertThat(codeProvider).isNotNull
        codeProvider?.let {
            assertThat(it.version.major).isEqualTo(1)
            assertThat(it.version.minor).isEqualTo(2)
        }
    }

    @Test
    fun shouldReturnCodeForPandas_1_3() {
        val codeProvider = PythonCodeProviderFactory.createProviderFor(PandasVersion.fromString("1.3.x"))

        assertThat(codeProvider).isNotNull
        codeProvider?.let {
            assertThat(it.version.major).isEqualTo(1)
            assertThat(it.version.minor).isEqualTo(3)
        }
    }

    @Test
    fun shouldReturnNullForNonSupportedVersion() {
        assertThat(
            PythonCodeProviderFactory.createProviderFor(PandasVersion.fromString("1.100.x"))
        ).isNull()
    }
}