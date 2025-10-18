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
package images.shared

import PipEnvProject
import java.io.File
import kotlin.collections.flatMap

/**
 * Represents a volume mapping between a host path and a container path.
 *
 * @property hostPath The path on the host machine
 * @property containerPath The path inside the container
 * @property readOnly Whether the volume should be mounted as read-only
 */
data class VolumeMapping(
    val hostPath: File,
    val containerPath: String,
    val readOnly: Boolean = false
)

/**
 * Centralized Docker path definitions for consistency across the build system.
 */
open class CommonDockerPaths(projectDir: File) {
    protected val containerAppRoot = "/usr/src/app"

    val transferIn = VolumeMapping(
        hostPath = projectDir.resolve("src/test-dockered/transfer/in"),
        containerPath = "$containerAppRoot/transfer/in",
        readOnly = true
    )

    val transferOut = VolumeMapping(
        hostPath = projectDir.resolve("src/test-dockered/transfer/out"),
        containerPath = "$containerAppRoot/transfer/out"
    )

    fun getContainerWorkDir(project: PipEnvProject): String {
        return "$containerAppRoot/projects/${project.name}"
    }
}

/**
 * Builder for constructing Docker volume mappings in a fluent way.
 */
class VolumeMappingBuilder<T : CommonDockerPaths>(val paths: T) {
    private val mappings = mutableListOf<String>()

    /**
     * Adds a volume mount mapping from a VolumeMapping.
     * The read-only mode is automatically determined from the VolumeMapping.
     */
    fun addMapping(volumeMapping: VolumeMapping) = apply {
        val mode = if (volumeMapping.readOnly) ":ro" else ""
        mappings.add("${volumeMapping.hostPath.absolutePath}:${volumeMapping.containerPath}$mode")
    }

    fun build(): List<String> = mappings

    /**
     * Returns a list with alternating "-v" and volume binding strings.
     */
    fun buildAsCliArguments(): Array<String> = mappings.flatMap { listOf("-v", it) }.toTypedArray()
}