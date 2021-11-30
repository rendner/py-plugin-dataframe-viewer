package cms.rendner.intellij.dataframe.viewer.core.html.extensions

import cms.rendner.intellij.dataframe.viewer.core.html.css.StyleDeclaration
import cms.rendner.intellij.dataframe.viewer.core.html.css.StyleDeclarationBlock
import org.w3c.dom.css.CSSStyleDeclaration

fun CSSStyleDeclaration.toStyleDeclarationBlock(): StyleDeclarationBlock = convertDeclarationBlock(this)

private fun convertDeclarationBlock(declarationBlock: CSSStyleDeclaration): StyleDeclarationBlock {
    return StyleDeclarationBlock(
        convertDeclarationProperty(declarationBlock, "color"),
        convertDeclarationProperty(declarationBlock, "background-color"),
        convertDeclarationProperty(declarationBlock, "text-align")
    )
}

private fun convertDeclarationProperty(declarationBlock: CSSStyleDeclaration, propertyName: String): StyleDeclaration? {
    return declarationBlock.getPropertyCSSValue(propertyName)?.let {
        StyleDeclaration(it.cssText, declarationBlock.getPropertyPriority(propertyName) === "important")
    }
}