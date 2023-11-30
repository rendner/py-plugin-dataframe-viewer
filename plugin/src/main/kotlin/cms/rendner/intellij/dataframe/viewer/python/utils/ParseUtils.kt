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
package cms.rendner.intellij.dataframe.viewer.python.utils

/**
 * Parses a stringified Python dictionary into a [Map] of strings.
 *
 * The following rules must be fulfilled by the [dictionary]:
 * - has to start with "{" and end with "}"
 * - keys and values don't contain the [delimiter]
 * - keys don't contain ":" (the key value separator)
 *
 * @param dictionary the stringified Python dictionary to parse
 * @param delimiter the entry separator used in [dictionary], default is ", "
 * @return the parsed result
 */
fun parsePythonDictionary(dictionary: String, delimiter: String = ", "): Map<String, String> {
    if (dictionary == "None") throw IllegalArgumentException("None not allowed.")
    return removeSurroundingAndSplitEntries(dictionary, "dictionary", '{', '}', delimiter)
        .associate { entry ->
            val separator = entry.indexOf(":")
            Pair(
                entry.substring(0, separator).removeSurrounding("'"),
                entry.substring(separator + 1).trim()
            )
        }
}

/**
 * Parses a stringified Python list into a [List] of strings.
 *
 * The following rules must be fulfilled by the [list]:
 * - has to start with "[" and end with "]"
 * - entries don't contain the [delimiter]
 *
 * @param list the stringified Python list to parse
 * @param delimiter the entry separator used in [list], default is ", "
 * @return the parsed result
 */
fun parsePythonList(list: String, delimiter: String = ", "): List<String> {
    if (list == "None") throw IllegalArgumentException("None not allowed.")
    return removeSurroundingAndSplitEntries(list, "list", '[', ']', delimiter)
}

/**
 * Parses a stringified Python tuple into a [List] of strings.
 *
 * The following rules must be fulfilled by the [tuple]:
 * - has to start with "(" and end with ")"
 * - entries don't contain the [delimiter]
 *
 * @param tuple the stringified Python tuple to parse
 * @param delimiter the entry separator used in [tuple], default is ", "
 * @return the parsed result
 */
fun parsePythonTuple(tuple: String, delimiter: String = ", "): List<String> {
    if (tuple == "None") throw IllegalArgumentException("None not allowed.")
    return removeSurroundingAndSplitEntries(tuple, "tuple", '(', ')', delimiter)
}

/**
 * Parses a Python bool into a [Boolean].
 */
fun parsePythonBool(value: String): Boolean {
    if (value == "None") throw IllegalArgumentException("None not allowed.")
    return value == "True"
}

/**
 * Parses a Python int into an [Int].
 */
fun parsePythonInt(value: String): Int {
    if (value == "None") throw IllegalArgumentException("None not allowed.")
    return value.toInt()
}

/**
 * Parses a Python string into a [String].
 */
fun parsePythonString(value: String): String {
    if (value == "None") throw IllegalArgumentException("None not allowed.")
    return value.removeSurrounding(value.first().toString())
}

private fun removeSurroundingAndSplitEntries(
    value: String,
    valueName: String,
    prefix: Char,
    suffix: Char,
    delimiter: String,
): List<String> {
    if (value.first() != prefix || value.last() != suffix) {
        throw NoSuchElementException("'$valueName' does not start with '$prefix' and end with '$suffix'")
    }
    if (value.length == 2) {
        return emptyList()
    }
    return value
        .removeSurrounding(prefix.toString(), suffix.toString())
        .split(delimiter)
}