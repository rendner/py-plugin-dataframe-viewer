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
package images.python_versions

import DataFrameProject
import DataFrameProjectsDockerImage

class PythonDockerImage(
    val project: DataFrameProject,
): DataFrameProjectsDockerImage(
    "sdfv-plugin-python-version_${project.pythonVersion}",
    listOf(project),
)

private fun toPythonDockerImage(version: String): PythonDockerImage {
    return PythonDockerImage(DataFrameProject(
        "python_$version",
        version,
        listOf(DataFrameModule.PANDAS, DataFrameModule.POLARS),
    ))
}

class PythonImageRegistry {
    companion object {
        val images = listOf(
            // order: latest to oldest
            toPythonDockerImage("3.13"),
            toPythonDockerImage("3.12"),
            toPythonDockerImage("3.11"),
            toPythonDockerImage("3.10"),
        )

        fun latest(): PythonDockerImage = images.first()
    }
}