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
import images.shared.CommonDockerPaths
import images.shared.VolumeMappingBuilder
import java.io.File

class DataFrameModule() {
    companion object {
        val PANDAS = "pandas"
        val POLARS = "polars"
    }
}

open class PipEnvProject(val name: String, val pythonVersion: String)

class DataFrameProject(
    name: String,
    pythonVersion: String,
    val installedDataFrameModules: List<String>,
): PipEnvProject(name, pythonVersion)

/**
 * Interface for Docker images that require volume mounting.
 */
interface DockerImageWithVolumes {
    /**
     * Creates a volume mapping builder configured with the base volume mappings
     * required by this Docker image to function.
     * These are the minimum volumes needed for the image to interact with the host.
     *
     * The caller can add additional volumes to the returned builder before building.
     *
     * @param projectDir The project directory for resolving paths
     * @return A configured builder with the required volume mappings
     */
    fun createConfiguredVolumeMappingBuilder(projectDir: File): VolumeMappingBuilder<out CommonDockerPaths> {
        return VolumeMappingBuilder(CommonDockerPaths(projectDir))
    }
}

abstract class DataFrameProjectsDockerImage(
    val dockerImageName: String,
    val projects: List<DataFrameProject>
): DockerImageWithVolumes
