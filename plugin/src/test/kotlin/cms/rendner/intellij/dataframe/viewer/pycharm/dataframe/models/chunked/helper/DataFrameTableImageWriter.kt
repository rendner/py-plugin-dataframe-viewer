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
package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.core.component.DataFrameTable
import cms.rendner.intellij.dataframe.viewer.core.component.models.IDataFrameModel
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JScrollPane


internal class DataFrameTableImageWriter {

    companion object {

        fun writeImage(dateFrameModel: IDataFrameModel, filePath: Path) {
            val table = DataFrameTable()

            table.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            table.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            table.addNotify()

            /*
            Set model after "table.addNotify()" call, because otherwise a JTable re-adds a header automatically, if:
                - parent was added to a component (simulated by "addNotify" call)
                - parent is a JScrollPane
                - was added to the scroll pane via "setViewPortView"
            But we want to hide the header of the value table if "dateFrameModel.getValueDataModel().shouldHideHeaders()"
            is set to true.
             */
            table.setDataFrameModel(dateFrameModel)

            table.size = calculateTableSize(table, dateFrameModel)
            layoutComponent(table)

            val image = UIUtil.createImage(table, table.width, table.height, BufferedImage.TYPE_INT_RGB)
            val g2d = image.createGraphics()
            table.paint(g2d)

            table.removeNotify()
            g2d.dispose()

            if(Files.notExists(filePath.parent)) {
                Files.createDirectories(filePath.parent)
            }

            ImageIO.write(image, "png", filePath.toFile())
        }

        private fun calculateTableSize(table:DataFrameTable, model: IDataFrameModel): Dimension {
            return if(model.getValueDataModel().rowCount == 0 && model.getValueDataModel().columnCount == 0) {
                Dimension(400, 100)
            } else {
                Dimension(
                    table.viewport.viewSize.width + table.rowHeader.viewSize.width,
                    (table.getRowHeight() * model.getValueDataModel().rowCount ) + table.columnHeader.viewSize.height
                )
            }
        }

        private fun layoutComponent(c: Component) {
            synchronized(c.treeLock) {
                c.doLayout()
                if (c is Container) {
                    for (child in c.components) {
                        layoutComponent(child)
                    }
                }
            }
        }
    }
}