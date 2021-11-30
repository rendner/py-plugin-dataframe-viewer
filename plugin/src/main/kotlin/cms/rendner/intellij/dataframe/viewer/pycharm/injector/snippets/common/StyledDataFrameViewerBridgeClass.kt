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