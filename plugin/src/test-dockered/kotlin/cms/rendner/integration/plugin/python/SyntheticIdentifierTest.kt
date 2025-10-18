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
package cms.rendner.integration.plugin.python

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.components.filter.SyntheticDataFrameIdentifier
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class SyntheticIdentifierTest : AbstractPluginCodeTest() {
    @Test
    fun shouldInitiallyNotAllowSyntheticIdentifier() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            Assertions.assertThat(debuggerApi.frameAccessor.syntheticIdentifierCanBeUsed()).isFalse()
        }
    }

    @Test
    fun shouldAllowSyntheticIdentifierIfItDoesNotShadowExistingObject() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            debuggerApi.updateSyntheticIdentifierSupportedFlag()
            Assertions.assertThat(debuggerApi.frameAccessor.syntheticIdentifierCanBeUsed()).isTrue()
        }
    }

    @Test
    fun shouldNotAllowSyntheticIdentifierIfItDoesNotShadowExistingObject() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            debuggerApi.frameAccessor.execute("${SyntheticDataFrameIdentifier.NAME} = 2")
            debuggerApi.updateSyntheticIdentifierSupportedFlag()
            Assertions.assertThat(debuggerApi.frameAccessor.syntheticIdentifierCanBeUsed()).isFalse()
        }
    }
}