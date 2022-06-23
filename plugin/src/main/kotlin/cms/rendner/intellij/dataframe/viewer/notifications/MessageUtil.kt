package cms.rendner.intellij.dataframe.viewer.notifications

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.JBScrollPane
import javax.swing.*


fun showHtmlMessageDialog(
    project: Project?,
    htmlMessage: String,
    title: String,
    messageType: Int,
    initMessageScrollPane:((JBScrollPane) -> Unit)? = null,
) {
    // worked fine in IntelliJ 2020.3 (correct width)
    // all later versions open a too small dialog which breaks the html layout of the htmlMessage
    /*
     Messages.showMessageDialog(
                project,
                htmlMessage,
                title,
                Messages.getInformationIcon(),
            )
     */
    // JLabel doesn't allow to select text, but copying a html table string result into a scrambled result on paste
    // therefore using JLabel instead of JEditorPane is on purpose
    val textComponent = JLabel(htmlMessage)
    val scrollPane = JBScrollPane(textComponent)
    scrollPane.border = null
    initMessageScrollPane?.let { it(scrollPane) }
    JOptionPane.showMessageDialog(
        WindowManager.getInstance().suggestParentWindow(project),
        scrollPane,
        title,
        messageType,
    )
}