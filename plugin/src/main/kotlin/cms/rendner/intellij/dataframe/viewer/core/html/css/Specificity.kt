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
package cms.rendner.intellij.dataframe.viewer.core.html.css

data class Specificity(val a: Int = 0, val b: Int = 0, val c: Int = 0) : Comparable<Specificity> {
    // ascending: low to high
    override fun compareTo(other: Specificity): Int {
        val a = a - other.a
        if (a == 0) {
            val b = b - other.b
            if (b == 0) {
                return this.c - other.c
            }
            return b
        }
        return a
    }
}