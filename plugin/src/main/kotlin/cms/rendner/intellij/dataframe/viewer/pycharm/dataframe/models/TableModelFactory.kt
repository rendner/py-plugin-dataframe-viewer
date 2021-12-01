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
package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models

import cms.rendner.intellij.dataframe.viewer.core.component.models.IDataFrameModel
import cms.rendner.intellij.dataframe.viewer.notifications.UserNotifier
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkedDataFrameModel
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.loader.AsyncChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.PyPatchedStylerRef

class TableModelFactory {

    companion object {
        private const val ROWS_PER_CHUNK = 60
        private const val COLUMNS_PER_CHUNK = 20

        /**
         * The chunked model evaluates the underlying dataframe in slices.
         */
        fun createChunkedModel(patchedStyler: PyPatchedStylerRef, notifier: UserNotifier? = null): IDataFrameModel {
            return ChunkedDataFrameModel(
                patchedStyler.evaluateTableStructure(),
                AsyncChunkDataLoader(
                    ChunkEvaluator(
                        patchedStyler,
                        ChunkSize(ROWS_PER_CHUNK, COLUMNS_PER_CHUNK)
                    ),
                    8,
                    notifier
                )
            )
        }
    }
}