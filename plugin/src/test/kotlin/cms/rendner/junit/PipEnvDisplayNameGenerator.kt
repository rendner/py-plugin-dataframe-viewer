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
package cms.rendner.junit

import cms.rendner.integration.python.debugger.DockeredPipenvEnvironment
import org.junit.jupiter.api.DisplayNameGenerator

class PipEnvDisplayNameGenerator : DisplayNameGenerator.Standard() {

    private val isIntegrationTest = System.getProperty("cms.rendner.dataframe.renderer.integration.test") == "true"
    private val pipenvEnvironment: String = if (isIntegrationTest) {
        val configuredEnvironment =
            System.getProperty("cms.rendner.dataframe.renderer.integration.test.pipenv.environment")
        DockeredPipenvEnvironment.labelOf(configuredEnvironment).label
    } else ""

    override fun generateDisplayNameForClass(testClass: Class<*>?): String {
        if (isIntegrationTest) {
            return "[pipenv env: ${pipenvEnvironment}] ${super.generateDisplayNameForClass(testClass)}"
        }
        return super.generateDisplayNameForClass(testClass)
    }
}