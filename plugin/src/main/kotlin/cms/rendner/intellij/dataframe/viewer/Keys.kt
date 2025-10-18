/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
@file:JvmName("KeysKt")

package cms.rendner.intellij.dataframe.viewer

import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterInputCompletionProvider
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableSourceContext
import com.intellij.openapi.util.Key

private const val KEY_PREFIX = "cms.rendner.intellij.dataframe.viewer"
val IGNORE_ALL_INTENTIONS_ACTION_FILTER_KEY = Key.create<Boolean>("$KEY_PREFIX.IGNORE_ALL_INTENTIONS_ACTION_FILTER")
val TABLE_SOURCE_CONTEXT_KEY = Key.create<TableSourceContext>("$KEY_PREFIX.TABLE_SOURCE_CONTEXT")
val COMPLETION_PROVIDER_KEY = Key.create<IFilterInputCompletionProvider>("$KEY_PREFIX.COMPLETION_PROVIDER")
