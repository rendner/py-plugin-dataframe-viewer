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
package cms.rendner.intellij.dataframe.viewer

enum class SystemPropertyEnum(val key: String) {
    DOCKERED_TEST_PIPENV_ENVIRONMENT("cms.rendner.dataframe.renderer.dockered.test.pipenv.environment"),
    ENABLE_TEST_DATA_EXPORT_ACTION("cms.rendner.dataframe.renderer.enable.test.data.export.action"),
    EXPORT_TEST_DATA_DIR("cms.rendner.dataframe.renderer.export.test.data.dir"),
    EXPORT_TEST_ERROR_IMAGE_DIR("cms.rendner.dataframe.renderer.export.test.error.image.dir")
}