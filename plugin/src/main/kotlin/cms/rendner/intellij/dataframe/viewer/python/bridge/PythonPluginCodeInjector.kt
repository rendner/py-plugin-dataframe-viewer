/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException

/**
 * Injects the Python-specific plugin code into the Python process.
 * The injected code is available until the Python process is terminated.
 *
 * There is no protection to ensure that code is injected only once into a Python process
 * when multiple threads attempt to inject the code. (should be OK for our use case)
 */
class PythonPluginCodeInjector {

    companion object {
        private const val PLUGIN_MODULE_NAME = "sdfv_plugin_code"

        /**
         * Returns the expression for accessing a specified class of the plugin-code module.
         */
        fun getFromPluginModuleExpr(name: String): String {
            // It is on purpose that the internal classes of the module can't be imported directly.
            // Since the module is only used by this plugin it is totally fine to use this exotic syntax
            // to access the classes of the module.
            return "__import__('$PLUGIN_MODULE_NAME').__dict__.get('$name')"
        }

        @Synchronized
        fun injectIfRequired(
            evaluator: IPluginPyValueEvaluator,
            pluginCodeEscaper: (code: String) -> String = ::defaultCodeEscaper,
        ) {
            // check if the code was already injected into the current Python process
            val doesExist =
                evaluator.evaluate("__import__('importlib').util.find_spec('$PLUGIN_MODULE_NAME') is not None").forcedValue
            if (doesExist == "True") return

            val version = try {
                PandasVersion.fromString(evaluator.evaluate("__import__('pandas').__version__").forcedValue)
            } catch (ex: EvaluateException) {
                throw InjectException("Failed to identify version of pandas.", ex)
            }

            val codeResourcePath = getPluginCodeResourcePath(version)
            val pluginCode = try {
                PythonPluginCodeInjector::class.java.getResource(codeResourcePath)!!.readText()
            } catch (ex: Throwable) {
                throw InjectException("Failed to read Python plugin code for $version.", ex)
            }

            /*
            Some Notes:

            1)
            The methods of the created MyPluginCodeImporter-instance are called sometime later.
            Therefore, it is important to define all required imports in the methods themselves, otherwise they are
            no longer available if they are defined at the top of the Python code snippet.

            Imports via __import__(...) are not visible in the PyCharm debugger afterwards.

            2)
            Linebreak-chars in strings passed to "exec" have to be escaped.

            Examples:

                default string behavior:
                    "a\nb".split("\n")                  => ['a', 'b']
                    "a\\nb".split("\n")                 => ['a\\nb']

                strings passed to exec:
                    exec("a = 'a\nb'.split('\n')")      => {SyntaxError}EOL while scanning string literal (<string>, line 1)
                    exec("a = 'a\\nb'.split('\\n')")    => ['a', 'b']
                    exec("a = 'a\\nb'")                 => 'a\nb'

              To refer to the un-escaped linebreak-chars in the "pluginCode", '\\n' has to be used.
             */
            try {
                evaluator.execute("""
                |class MyPluginCodeImporter(__import__('importlib').abc.MetaPathFinder, __import__('importlib').abc.Loader):
                |   def find_spec(self, fullname, path=None, target=None):
                |       if fullname == "$PLUGIN_MODULE_NAME":
                |           return __import__('importlib').util.spec_from_loader(fullname, self)
                |       return None
                |
                |   def exec_module(self, module) -> None:
                |       exec('''${pluginCodeEscaper(pluginCode)}''', module.__dict__)
                |
                |__import__('sys').meta_path.append(MyPluginCodeImporter())
                |# cleanup to hide the class after use
                |del MyPluginCodeImporter
                """.trimMargin()
                )
            } catch (ex: EvaluateException) {
                throw InjectException("Failed to inject Python plugin code for $version.", ex)
            }
        }

        private fun defaultCodeEscaper(code: String): String {
            return code
                .replace("\\n", "\\\\n")
                .replace("\\t", "\\\\t")
        }

        private fun getPluginCodeResourcePath(version: PandasVersion): String {
            if (version.major == 1) {
                if (version.minor in 1..5) {
                    return "/pandas_1.${version.minor}/plugin_code"
                }
            } else if (version.major == 2) {
                if (version.minor in 0..0) {
                    return "/pandas_2.${version.minor}/plugin_code"
                }
            }
            throw InjectException("Unsupported $version.")
        }
    }
}