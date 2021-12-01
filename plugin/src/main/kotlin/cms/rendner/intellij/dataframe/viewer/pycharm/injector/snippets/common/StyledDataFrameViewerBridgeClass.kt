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
package cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.common

/*
    This bridge hides all created "patchedStyler" instances, to not pollute the debugger view,
    by collecting them in an internal list.
*/
val pythonStyledDataFrameViewerBridgeClass = """
import pandas as pd


class StyledDataFrameViewerBridge:

    patched_styler_refs = []
    
    @classmethod
    def create_patched_styler(cls, frame_or_styler) -> PatchedStyler:
        p = PatchedStyler(frame_or_styler.style) if isinstance(frame_or_styler, pd.DataFrame) else PatchedStyler(frame_or_styler)
        cls.patched_styler_refs.append(p)
        return p
        
    @classmethod
    def delete_patched_styler(cls, patched_styler: PatchedStyler):
        cls.patched_styler_refs.remove(patched_styler)
        del patched_styler
        
    @staticmethod
    def check() -> bool:
        return True

""".trimIndent()