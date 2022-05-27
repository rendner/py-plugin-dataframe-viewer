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
package cms.rendner.intellij.dataframe.viewer

enum class SystemPropertyEnum(val key: String) {
    /**
     * The working directory inside the docker container.
     * (used by integration tests)
     */
    DOCKERED_TEST_WORKDIR("cms.rendner.dataframe.viewer.dockered.test.workdir"),

    /**
     * The docker image to run.
     * (used by integration tests)
     */
    DOCKERED_TEST_IMAGE("cms.rendner.dataframe.viewer.dockered.test.image"),

    /**
     * Enables the export-test-data action if set to "true".
     * (for local development)
     */
    ENABLE_TEST_DATA_EXPORT_ACTION("cms.rendner.dataframe.viewer.enable.test.data.export.action"),

    /**
     * The target directory for the exported test-data.
     * (for local development)
     */
    EXPORT_TEST_DATA_DIR("cms.rendner.dataframe.viewer.export.test.data.dir"),

    /**
     * The target directory for the test screenshots in case of an error.
     * (for unit tests)
     */
    EXPORT_TEST_ERROR_IMAGE_DIR("cms.rendner.dataframe.viewer.export.test.error.image.dir"),
}