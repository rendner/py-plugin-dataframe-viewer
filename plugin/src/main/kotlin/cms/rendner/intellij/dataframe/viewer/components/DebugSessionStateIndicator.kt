/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * A component that displays a colored dot indicator representing the debug session state.
 */
class DebugSessionStateIndicator : JComponent() {

    enum class State(val color: Color, val tooltipText: String) {
        STOPPED(Color(96, 96, 96), "Debug session terminated"),
        RESUMED(Color(255, 165, 70), "Data fetching disabled (debug session resumed)"),
        PAUSED(Color(102, 187, 106), "Data fetching enabled (debug session paused)")
    }

    private var currentState: State = State.PAUSED
    private val dotSize = 8

    init {
        preferredSize = Dimension(dotSize + 4, dotSize + 4)
        minimumSize = preferredSize
        maximumSize = preferredSize
        isOpaque = false
        updateTooltip()
    }

    /**
     * Sets the current state of the debug session.
     */
    fun setState(state: State) {
        if (currentState != state) {
            currentState = state
            updateTooltip()
            repaint()
        }
    }

    private fun updateTooltip() {
        toolTipText = currentState.tooltipText
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val x = (width - dotSize) / 2
        val y = (height - dotSize) / 2

        g2d.color = currentState.color
        g2d.fillOval(x, y, dotSize, dotSize)
    }
}
