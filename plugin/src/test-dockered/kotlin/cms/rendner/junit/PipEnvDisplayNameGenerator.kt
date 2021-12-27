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

import cms.rendner.debugger.AbstractPipEnvEnvironmentTest
import cms.rendner.debugger.impl.PipenvEnvironment
import cms.rendner.intellij.dataframe.viewer.SystemPropertyEnum
import org.junit.jupiter.api.DisplayNameGenerator

class PipEnvDisplayNameGenerator : DisplayNameGenerator.Standard() {

    private val pipEnvTestSuperClass = AbstractPipEnvEnvironmentTest::class.java
    private val pipenvEnvironment = PipenvEnvironment.labelOf(
        System.getProperty(SystemPropertyEnum.DOCKERED_TEST_PIPENV_ENVIRONMENT.key)
    )

    override fun generateDisplayNameForClass(testClass: Class<*>?): String {
        if (testClass != null && pipEnvTestSuperClass.isAssignableFrom(testClass)) {
            return "[pipenv env: ${pipenvEnvironment.label}] ${super.generateDisplayNameForClass(testClass)}"
        }
        return super.generateDisplayNameForClass(testClass)
    }
}