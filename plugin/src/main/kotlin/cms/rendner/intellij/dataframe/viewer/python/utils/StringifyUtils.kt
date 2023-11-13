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
 * Converts a [Boolean] into the Python bool literal "True" or "False".
 *
 * @param value the value to convert
 */
fun stringifyBool(value: Boolean) = if (value) "True" else "False"

/**
 * Converts a [String] into a Python string literal (by surrounding it with quotes).
 *
 * @param value the value to convert
 * @param singleQuotes if true [value] is surrounded with single quotes otherwise with double quotes
 */
fun stringifyString(value: String, singleQuotes: Boolean = true) = if (singleQuotes) "'$value'" else "\"$value\""

/**
 * Builder to build a Python method call.
 *
 * @param instance the object on which the method should be called
 * @param methodName the name of the method to be called
 * @param init a receiver function to specify the parameters of the method call
 */
fun stringifyMethodCall(instance: String, methodName: String, init: (PythonCallBuilder.()-> Unit)? = null):String {
    val builder = PythonCallBuilder(methodName, instance)
    init?.let { builder.apply(it) }
    return builder.toString()
}

fun stringifyImportWithObjectRef(moduleName: String, objectName: String): String {
    val firstPeriod = moduleName.indexOf('.')
    // __import__ returns always the first module of the module path
    // therefore the module path to the specified object has to be appended, excluding the first package, afterward
    return "__import__('${moduleName}')${moduleName.substring(firstPeriod)}.$objectName"
}

class PythonCallBuilder(
    private val name: String,
    private val instance: String? = null,
) {
    private val sb = StringBuilder()
    private var isFirst = true

    /**
     * To specify the Python literal None.
     */
    fun noneParam() = addParam("None")

    /**
     * To specify an existing Python object instance.
     */
    fun refParam(value: String) = addParam(value)

    /**
     * To specify a Python boolean.
     */
    fun boolParam(value: Boolean) = addParam(stringifyBool(value))

    /**
     * To specify a Python string literal.
     * The string is automatically surrounded with single quotes.
     */
    fun stringParam(value: String) = addParam(stringifyString(value))

    /**
     * To specify a Python number value.
     */
    fun numberParam(value: Number) = addParam(value.toString())

    override fun toString(): String {
        if(instance == null) {
            return "$name($sb)"
        }
        return "$instance.$name($sb)"
    }

    private fun addParam(value: String) {
        if (isFirst) {
            isFirst = false
        } else {
            sb.append(", ")
        }
        sb.append(value)
    }
}