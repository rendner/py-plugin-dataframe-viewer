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
fun createJSQueryCode(): String {
    return """
        ${getJSMethodComputeElementRecursiveNew()}
        const table = document.getElementsByTagName("table")[0];
        const styledTable = computeStyledTable(table);
        window.cefQuery({
            request: JSON.stringify(styledTable, null, 2),
            onSuccess: function(response) {
                console.log('cefQuery-Success-Response: ' + response);
            },
            onFailure: function(error_code, error_message) {
                console.log('cefQuery-Failure-Response: ' + error_message);
            }
        });
    """.trimIndent()
}

private fun getJSMethodComputeElementRecursiveNew(): String {
    return """
        function computeStyledTable(element) {
            const computedElement = computeElementRecursive(element);
            let head = null;
            let body = null;
            for (let i = 0; i < computedElement.children.length; i++) {
                const child = computedElement.children[i];
                if (child.type == "thead") head = child;
                else if (child.type == "tbody") body = child;
            }
            delete computedElement["children"];
            computedElement["head"] = head;
            computedElement["body"] = body;
            return computedElement;
        }
        
        function computeElementRecursive(element) {
            const cs = window.getComputedStyle(element);
            const styles = {
                "backgroundColor": `${"$"}{cs.getPropertyValue('background-color')}`,
                "color": `${"$"}{cs.getPropertyValue('color')}`,
                "textAlign": `${"$"}{cs.getPropertyValue('text-align')}`,
            };
            const computedElement = {
                "styles": styles, 
                "type": element.tagName.toLowerCase(), 
            };
            let text = "";
            const children = [];
            if (element.hasChildNodes()) {
                for (let i = 0; i < element.childNodes.length; i++) {
                    const child = element.childNodes[i];
                    if (child.nodeType === Node.ELEMENT_NODE) {
                        children.push(computeElementRecursive(child));
                    } else if (child.nodeType === Node.TEXT_NODE) {
                        text += child.textContent
                    }
                }
            }
            if (computedElement.type == "th" || computedElement.type == "td") {
                computedElement["text"] = text
            } else {
                 computedElement["children"] = children
            }
            return computedElement;
        }
    """.trimIndent()
}