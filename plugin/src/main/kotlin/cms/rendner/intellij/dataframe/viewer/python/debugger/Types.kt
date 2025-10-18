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
package cms.rendner.intellij.dataframe.viewer.python.debugger

import cms.rendner.intellij.dataframe.viewer.components.filter.SyntheticDataFrameIdentifier
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.python.console.pydev.PydevCompletionVariant
import java.util.Base64

/**
 * Describes a Python object or a result evaluated on Python side.
 *
 * @property value the evaluation result as string
 * @property type the class type of the Python object
 * @property typeQualifier a dotted name build from the name of module in which the
 * objects class was defined combined with the qualified name of the objects class.
 * @property refExpr a Python expression to access the object for follow-up evaluations
 */
data class PluginPyValue(
    val value: String?,
    val type: String,
    val typeQualifier: String,
    val refExpr: String,
) {
    /**
     * Asserts non-null and returns [value]
     */
    val forcedValue: String
        get() = value!!

    val qualifiedType: String
        get() = if (typeQualifier.isNotEmpty()) "$typeQualifier.$type" else type
}

/**
 * Interface for evaluating or executing code on Python side.
 */
interface IPluginPyFrameAccessor {
    /**
     * Returns true if the underlying Python debug session or console is still active and can accept evaluations.
     * Used to prevent operations on terminated sessions.
     */
    fun isAlive(): Boolean

    /**
     * Returns a unique identifier for this frame accessor instance.
     * Used as a key to track state across services (e.g., plugin code injection state, frame accessor mappings).
     */
    fun getUniqueId(): String

    /**
     * Returns a human-readable display name.
     * Used in dialog titles to provide context to the user (e.g., "Python Console" or session name).
     */
    fun getDisplayName(): String? = null

    /**
     * Returns true if a synthetic identifier referencing the DataFrame can be used in expressions.
     * The synthetic identifier allows convenient access to the DataFrame
     * in filter expressions without re-evaluating the original DataFrame expression.
     */
    fun syntheticIdentifierCanBeUsed(): Boolean

    /**
     * Evaluates an expression.
     *
     * @param expression the expression to evaluate
     * @param trimResult if true, the result is shortened.
     * The length of the shortened results depends on the underlying implementation of the evaluator.
     * @throws [EvaluateException] in case the evaluation failed, regardless of whether the error is
     * caused by invalid Python syntax or by the underlying evaluator.
     */
    @Throws(EvaluateException::class)
    fun evaluate(expression: String, trimResult: Boolean = false): PluginPyValue

    /**
     * Executes statements.
     *
     * @param statement the statement to execute
     * @throws [EvaluateException] in case the exec failed, regardless of whether the error is
     * caused by invalid Python syntax or by the underlying evaluator.
     */
    @Throws(EvaluateException::class)
    fun execute(statement: String)

    /**
     * Returns the current source position of the current stack frame, or null if not available.
     */
    fun getCurrentSourcePosition(): XSourcePosition? = null

    /**
     * Returns completion variants for the given [text] and [actTok].
     *
     * @param text the full text, or as much context as available, for which the completion is requested.
     *              Used to understand the surrounding code context.
     * @param actTok the actual token to complete.
 *                  Used to filter/match completion results.
     */
    fun getConsoleCompletions(text: String, actTok: String): List<PydevCompletionVariant> {
        return emptyList()
    }

    /**
     * Returns true if the evaluator is based on a console (like PyCharm's Debug Console).
     * The Python console based "frameAccessor" doesn't provide a sourcePosition which could be used for code completion.
     * Instead, the data has to be fetched from the underlying Python process.
     */
    fun isDebugConsole(): Boolean = false

    fun toEscapedSingleLineExec(statement: String): String {
        // While JSON encoding escapes newlines, it still fails with certain edge cases
        // (e.g., raw strings, triple quotes).
        // Base64 encoding ensures the statement is always a single-line string and does
        // not have the escaping problems.
        val encoded = Base64.getEncoder().encodeToString(statement.toByteArray())
        return "exec(__import__('base64').b64decode('$encoded').decode('utf-8'))"
    }
}

abstract class AbstractPluginFrameAccessor: IPluginPyFrameAccessor {
    @Volatile
    private var mySyntheticIdentifierCanBeUsed = false

    override fun syntheticIdentifierCanBeUsed() = mySyntheticIdentifierCanBeUsed

    fun updateSyntheticIdentifierSupportedFlag() {
        mySyntheticIdentifierCanBeUsed = false
        try {
            // check if identifier is used without printing the identifier's value
            // the printed output of id is less than a printed DataFrame (even if it is truncated)
            evaluate("id(${SyntheticDataFrameIdentifier.NAME})")
        } catch (ex: EvaluateException) {
            if (ex.pythonErrorQName == PythonQualifiedTypes.NameError) {
                mySyntheticIdentifierCanBeUsed = true
            }
        }
    }
}
