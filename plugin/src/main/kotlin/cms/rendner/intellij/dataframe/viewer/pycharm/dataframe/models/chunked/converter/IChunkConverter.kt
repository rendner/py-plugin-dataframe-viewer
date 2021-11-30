package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkValues

interface IChunkConverter {
    fun convertText(excludeRowHeader: Boolean, excludeColumnHeader: Boolean): ChunkData
    fun mergeWithStyles(values: ChunkValues): ChunkValues
}