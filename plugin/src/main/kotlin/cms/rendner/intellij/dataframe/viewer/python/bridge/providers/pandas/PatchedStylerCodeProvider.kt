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
package cms.rendner.intellij.dataframe.viewer.python.bridge.providers.pandas

import cms.rendner.intellij.dataframe.viewer.python.PandasTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceFactoryImport
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceKind

class PatchedStylerCodeProvider : BaseCodeProvider() {
    override fun getFactoryImport(): TableSourceFactoryImport {
        return TableSourceFactoryImport(
            "cms_rendner_sdfv.pandas.styler.table_source_factory",
            "TableSourceFactory",
            TableSourceKind.PATCHED_STYLER,
        )
    }

    override fun isApplicable(fqClassName: String): Boolean {
        return PandasTypes.isStyler(fqClassName)
    }
}