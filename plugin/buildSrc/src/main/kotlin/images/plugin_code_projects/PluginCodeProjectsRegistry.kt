/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
package images.plugin_code_projects

import DataFrameModule
import DataFrameProject
import DataFrameProjectsDockerImage
import PipEnvProject
import images.shared.CommonDockerPaths
import images.shared.VolumeMapping
import images.shared.VolumeMappingBuilder
import java.io.File

class PluginCodeProjectsImage(
    val baseProject: PipEnvProject,
    implementingProjects: List<DataFrameProject>,
): DataFrameProjectsDockerImage(
    "sdfv-plugin-code-projects",
    implementingProjects
) {
    val pythonVersions: List<String> by lazy {
        (projects.map { it.pythonVersion } + baseProject.pythonVersion)
            .toSortedSet(compareBy { version ->
                val (major, minor) = version.split('.').map { it.toInt() }
                major * 100 + minor
            })
            .toList()
    }

    class PythonPluginCodeDockerPaths(projectDir: File) : CommonDockerPaths(projectDir) {
        val pluginCodeProjects = VolumeMapping(
            hostPath = projectDir.parentFile.resolve("projects/python_plugin_code"),
            containerPath = "$containerAppRoot/projects",
            readOnly = true
        )

        val pluginCodeProjectsGeneratedDumps = VolumeMapping(
            hostPath = projectDir.parentFile.resolve("projects/python_plugin_code/generated_dumps"),
            containerPath = "$containerAppRoot/projects/generated_dumps"
        )

        fun getContainerScriptPath(scriptName: String): String {
            return "$containerAppRoot/scripts/$scriptName"
        }
    }

    override fun createConfiguredVolumeMappingBuilder(projectDir: File): VolumeMappingBuilder<PythonPluginCodeDockerPaths> {
        // todo: document that the folder has to be configured when using Docker Desktop
        // otherwise a permission error is returned when interacting with
        // -> These directories (and their subdirectories) can be bind mounted into Docker
        return VolumeMappingBuilder(PythonPluginCodeDockerPaths(projectDir)).apply {
            addMapping(paths.pluginCodeProjects)
        }
    }
}


private val pluginCodeProjectsImage = PluginCodeProjectsImage(
    PipEnvProject("sdfv_base", "3.7"),
    listOf(
        DataFrameProject(
            "pandas_1.1",
            "3.7",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_1.2",
            "3.7",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_1.3",
            "3.7",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_1.4",
            "3.8",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_1.5",
            "3.8",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_2.0",
            "3.8",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_2.1",
            "3.9",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_2.2",
            "3.9",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_2.3",
            "3.9",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "pandas_3.0",
            "3.11",
            listOf(DataFrameModule.PANDAS)
        ),
        DataFrameProject(
            "polars_x.y",
            "3.8",
            listOf(DataFrameModule.POLARS)
        )
    ),
)

class PluginCodeProjectsRegistry {
    companion object {
        val image: PluginCodeProjectsImage = pluginCodeProjectsImage

        fun getAllProjects(): List<DataFrameProject> = image.projects

        fun getProjectByName(name: String): DataFrameProject? {
            return image.projects.find { it.name == name }
        }

        fun getProjectsByDataFrameModule(module: String): List<DataFrameProject> {
            return image.projects.filter { module in it.installedDataFrameModules }
        }
    }
}
