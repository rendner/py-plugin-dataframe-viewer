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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.css

import org.w3c.css.sac.Selector

data class StyleDeclarationBlock(
    val textColor: StyleDeclaration? = null,
    val backgroundColor: StyleDeclaration? = null,
    val textAlign: StyleDeclaration? = null
)

data class StyleDeclaration(
    val value: String,
    val important:Boolean = false
)

data class MutableStyleDeclarationBlock(
    val textColor: MutableStyleDeclaration = MutableStyleDeclaration(),
    val backgroundColor: MutableStyleDeclaration = MutableStyleDeclaration(),
    val textAlign: MutableStyleDeclaration = MutableStyleDeclaration()
) {
    fun merge(style: StyleDeclarationBlock) {
        textColor.merge(style.textColor)
        backgroundColor.merge(style.backgroundColor)
        textAlign.merge(style.textAlign)
    }
}

data class MutableStyleDeclaration(
    var value: String? = null,
    var important:Boolean = false
){

    fun merge(source: StyleDeclaration?){
        source?.let {
            merge(it.value, it.important)
        }
    }

    private fun merge(newValue: String, valueIsImportant: Boolean){
        if (valueIsImportant || !important) {
            value = newValue
            important = valueIsImportant
        }
    }
}

data class RuleSet(
    val selectors: GroupedSelectors = GroupedSelectors(),
    val declarationBlock: StyleDeclarationBlock = StyleDeclarationBlock(),
    val ordinalIndex:Int
)

data class GroupedSelectors(
    val simpleIdSelectors: Map<String, Selector> = emptyMap(),
    val simpleClassSelectors: Map<String, Selector> = emptyMap(),
    val otherSelectors: List<Selector> = emptyList()
)