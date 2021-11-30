@file:Suppress("KDocUnresolvedReference")

package cms.rendner.intellij.dataframe.viewer.pycharm.evaluator

import com.intellij.xdebugger.frame.XValueChildrenList
import com.jetbrains.python.debugger.PyDebugValue
import java.util.*
import kotlin.math.min

/**
 * Allows to iterator over large lists (more than 100 elements).
 * By default [PyFrameAccessor] evaluates max 100 elements per [PyFrameAccessor.loadVariable] call.
 *
 * The method [PyDebugValue.computeChildren] can't be used because the implementation doesn't work as
 * expected. The parameter "last" of [XCompositeNode.addChildren] is always "true" and [PyDebugValue.myOffset]
 * is updated after [XCompositeNode.addChildren] and [XCompositeNode.tooManyChildren] was called.
 * There is also a hint that the interface [XCompositeNode] isn't supposed to be implemented by a plugin.
 *
 */
class ListPartsIterator(list: PyDebugValue): Iterator<List<PyDebugValue>> {

    // "PyDebugValue::offset" could > 0, create a copy and set it explicit to 0
    private val list: PyDebugValue = PyDebugValue(list).apply { offset = 0 }
    private var listSize = -1

    override fun next(): List<PyDebugValue> {
        if(!hasNext()) throw NoSuchElementException()
        // "values" always contains "__len__" as last element
        val values = list.frameAccessor.loadVariable(list).also {
            updateSize(it)
            if(it.size() > 1) {
                list.offset = min(list.offset + it.size() - 1, listSize)
            }
        }
        return convertList(values)
    }

    override fun hasNext() = listSize == -1 || list.offset < listSize

    private fun updateSize(values: XValueChildrenList) {
        // only the first list of values contains the correct size information
        if (listSize > -1) return

        val lastIndex = values.size() - 1
        listSize = if (values.getName(lastIndex) == "__len__") {
            val len = values.getValue(lastIndex) as PyDebugValue
            len.value?.toIntOrNull() ?: 0
        } else {
            0
        }
    }

    private fun convertList(values: XValueChildrenList): List<PyDebugValue> {
        val result = ArrayList<PyDebugValue>()
        for (i in 0 until values.size() - 1) {
            values.getValue(i).let {
                if (it is PyDebugValue) {
                    result.add(it)
                }
            }
        }
        return result
    }
}