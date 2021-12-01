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
package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter.exceptions

class ConvertException(message: String, private val additionalMessage: String? = null): Exception(message) {

    override fun getLocalizedMessage(): String {
        val msg = super.getLocalizedMessage()
        return additionalMessage?.let {
            if(it.length <= 80) {
                return "$msg $it"
            }
            return "$msg ${it.substring(0, 80)}"
        } ?: msg
    }
}