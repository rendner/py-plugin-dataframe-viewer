/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary

object TestProperty {
    /**
     * The docker image to run.
     */
    fun getDockerImage(): String = System.getProperty("cms.rendner.dataframe.viewer.docker.image")

    /**
     * The working directory inside the docker container.
     */
    fun getDockerWorkdir(): String = System.getProperty("cms.rendner.dataframe.viewer.docker.workdir", "")

    /**
     * The volumes to map inside the docker container.
     */
    fun getDockerVolumes(): List<String> {
        // string can contain multiple volumes, separated by ";"
        return System.getProperty("cms.rendner.dataframe.viewer.docker.volumes", "")
            .split(";")
            .map { it.trim() }
    }

    /**
     * The available data-frame libraries in the pipenv environment under test.
     */
    fun getDataFrameLibraries(): List<DataFrameLibrary> {
        return System.getProperty("cms.rendner.dataframe.viewer.dataframe.libraries", "")
            .removeSurrounding(prefix = "[", suffix = "]")
            .split(",")
            .map { DataFrameLibrary(it.trim()) }
    }
}