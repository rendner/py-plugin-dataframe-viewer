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

object TestSystemPropertyKey {
    /**
     * The docker image to run.
     */
    const val DOCKER_IMAGE = "cms.rendner.dataframe.viewer.docker.image"

    /**
     * The working directory inside the docker container.
     */
    const val DOCKER_WORKDIR = "cms.rendner.dataframe.viewer.docker.workdir"

    /**
     * The volumes to map inside the docker container.
     * The string can contain multiple volumes, separated by ";".
     */
    const val DOCKER_VOLUMES = "cms.rendner.dataframe.viewer.docker.volumes"
}