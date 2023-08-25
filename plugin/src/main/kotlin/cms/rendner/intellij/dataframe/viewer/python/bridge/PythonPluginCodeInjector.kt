/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
            pandasVersion: PandasVersion,
            evaluator: IPluginPyValueEvaluator,
            pluginCodeEscaper: (code: String) -> String = ::defaultCodeEscaper,
        ) {
            // check if the code was already injected into the current Python process
            val doesExist =
                evaluator.evaluate("__import__('importlib').util.find_spec('$PLUGIN_MODULE_NAME') is not None").forcedValue
            if (doesExist == "True") return

            val codeResourcePath = getPluginCodeResourcePath(pandasVersion)
            val pluginCode = try {
                PythonPluginCodeInjector::class.java.getResource(codeResourcePath)!!.readText()
            } catch (ex: Throwable) {
                throw InjectException("Failed to read Python plugin code for $pandasVersion.", ex)
            }

            /*
            Some Notes:

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
                |def sdfv_plugin_code_injector():
                |   from importlib import abc
                |   import sys
                |
                |   class MyPluginCodeImporter(abc.MetaPathFinder, abc.Loader):
                |       def find_spec(self, fullname, path=None, target=None):
                |           if fullname == "$PLUGIN_MODULE_NAME":
                |               from importlib import util
                |               return util.spec_from_loader(fullname, self)
                |           return None
                |
                |       def exec_module(self, module) -> None:
                |           exec('''${pluginCodeEscaper(pluginCode)}''', module.__dict__)
                |
                |   sys.meta_path.append(MyPluginCodeImporter())
                |
                |try:
                |   sdfv_plugin_code_injector()
                |finally:
                |   del sdfv_plugin_code_injector
                """.trimMargin()
                )
            } catch (ex: EvaluateException) {
                throw InjectException("Failed to inject Python plugin code for $pandasVersion.", ex)
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