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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.extensions

import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.css.StyleDeclaration
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.css.StyleDeclarationBlock
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