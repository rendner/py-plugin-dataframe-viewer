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
fun createJSQueryCode(): String {
    return """
        ${getJSMethodComputeElementRecursive()}
        ${getJSMethodBuildStyleAttributeString()}
        const table = document.getElementsByTagName("table")[0];
        const result = computeElementRecursive(table);
        window.cefQuery({
            request: result,
            onSuccess: function(response) {
                console.log('cefQuery-Success-Response: ' + response);
            },
            onFailure: function(error_code, error_message) {
                console.log('cefQuery-Failure-Response: ' + error_message);
            }
        });
    """.trimIndent()
}

private fun getJSMethodComputeElementRecursive(): String {
    return """
        function computeElementRecursive(element) {
            const tag = element.tagName.toLowerCase();
            const compStyles = window.getComputedStyle(element);
            const styleAttr = buildStyleAttributeString(element);
            let computedChildren = "";
            let text = "";
            if (element.hasChildNodes()) {
                const children = element.childNodes;
                for (let i = 0; i < children.length; i++) {
                    const child = children[i];
                    if (child.nodeType === Node.ELEMENT_NODE) {
                        computedChildren += computeElementRecursive(children[i]);
                    } else if (child.nodeType === Node.TEXT_NODE) {
                        text += child.textContent
                    }
                }
            }
            let result = "<" + tag;
            if (element.className.length > 0) result += " class='" + element.className + "'";
            if (styleAttr.length > 0) result += " " + styleAttr;
            if (computedChildren.length > 0) {
                result += ">" + computedChildren + "</" + tag + ">"
            } else if (text.length > 0) {
                result += ">" + text + "</" + tag + ">"
            } else {
                result += "/>";
            }
            return result;
        }
    """.trimIndent()
}

private fun getJSMethodBuildStyleAttributeString(): String {
    return """
        function buildStyleAttributeString(element) {
            const cs = window.getComputedStyle(element);
            let result = "style='";
            result += `background-color:${"$"}{cs.getPropertyValue('background-color')};`;
            result += `color:${"$"}{cs.getPropertyValue('color')};`;
            result += `text-align:${"$"}{cs.getPropertyValue('text-align')};`;
            result += "'";
            return result;
        }
    """.trimIndent()
}