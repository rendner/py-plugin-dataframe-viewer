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
package cms.rendner.intellij.dataframe.viewer.components

import cms.rendner.intellij.dataframe.viewer.COMPLETION_PROVIDER_KEY
import cms.rendner.intellij.dataframe.viewer.TABLE_SOURCE_CONTEXT_KEY
import cms.rendner.intellij.dataframe.viewer.components.filter.*
import cms.rendner.intellij.dataframe.viewer.components.table.DataFrameTable
import cms.rendner.intellij.dataframe.viewer.components.table.models.EmptyDataFrameModel
import cms.rendner.intellij.dataframe.viewer.components.table.models.chunked.AsyncDataFrameModel
import cms.rendner.intellij.dataframe.viewer.components.table.models.chunked.AutoFetchingTableSource
import cms.rendner.intellij.dataframe.viewer.components.table.models.chunked.AutoFetchingTableSourceListener
import cms.rendner.intellij.dataframe.viewer.components.table.renderer.ValueCellRenderer
import cms.rendner.intellij.dataframe.viewer.components.table.renderer.styling.CellStylingMode
import cms.rendner.intellij.dataframe.viewer.components.table.renderer.styling.cells.CellStyleComputer
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.TableSourceException
import cms.rendner.intellij.dataframe.viewer.services.FrameAccessorManager
import cms.rendner.intellij.dataframe.viewer.services.IFrameAccessorListener
import cms.rendner.intellij.dataframe.viewer.services.SharedExecutorService
import cms.rendner.intellij.dataframe.viewer.services.SyntheticDataFramePsiRefProvider
import cms.rendner.intellij.dataframe.viewer.settings.ApplicationSettingsService
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.FontColor
import java.awt.Dimension
import java.awt.event.ActionListener
import java.text.NumberFormat
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.event.RowSorterListener

class DataFrameViewerDialog(
    private val project: Project,
    private val tableSourceRef: IPyTableSourceRef,
    ) : DialogWrapper(project, false),
        AutoFetchingTableSourceListener {

    private val myTable: DataFrameTable
    private val myTableFooterLabel = JBLabel("", UIUtil.ComponentStyle.SMALL, FontColor.BRIGHTER)

    private val myCellStylingModeComboBox: CellStylingComboBox

    private val myDataFetcher: AutoFetchingTableSource

    private var myDataFrameModel: AsyncDataFrameModel? = null
    private var myFilterInput: AbstractFilterInput? = null

    private val myDebugStateIndicator = DebugSessionStateIndicator()

    init {
        isModal = false

        title = tableSourceRef.context.let { ctx ->
            ctx.frameAccessor.getDisplayName()?.let {
                "$it: ${ctx.dataSourceInfo.source.displayName}"
            } ?: ctx.dataSourceInfo.source.displayName
        }

        // dialog doesn't sync on settings, user has to re-open the dialog after settings are changed
        val pluginSettings = ApplicationSettingsService.instance.state

        setOKButtonText("Apply Filter")
        setCancelButtonText("Close")
        setCrossClosesWindow(true)

        val cellStyleComputer = CellStyleComputer()

        myTable = DataFrameTable(pluginSettings.showDTypeInColumnHeader)
        myTable.preferredSize = Dimension(635, 404)
        myTable.getValuesTable().setDefaultRenderer(Object::class.java, ValueCellRenderer(cellStyleComputer))

        myCellStylingModeComboBox = CellStylingComboBox().apply {
            cellStyleComputer.setStylingMode(pluginSettings.defaultCellStylingMode)
            selectedItem = pluginSettings.defaultCellStylingMode
            val listener = ActionListener {
                cellStyleComputer.setStylingMode(selectedItem as CellStylingMode)
                myTable.getValuesTable().model?.fireTableDataChanged()
            }
            addActionListener(listener)
            Disposer.register(disposable) { removeActionListener(listener) }
        }

        myDataFetcher = AutoFetchingTableSource(
            project.service<SharedExecutorService>(),
            tableSourceRef,
            pluginSettings.pandasStyledFuncValidationEnabled,
            project,
        ).also {
            Disposer.register(disposable, it)
            it.registerViewportWatcher(myTable)
            it.addListener(this)
            Disposer.register(disposable) { it.removeListener(this) }
        }

        if (tableSourceRef.context.dataSourceInfo.filterable) {
            createFilterInput(tableSourceRef, pluginSettings).also {
                myFilterInput = it
                it.setSourcePosition(tableSourceRef.context.frameAccessor.getCurrentSourcePosition())
            }
        }
        disableApplyFilterButton()

        val sessionLifecycleListener = object: IFrameAccessorListener {
            override fun sessionStopped() = runInEdt {
                myDebugStateIndicator.setState(DebugSessionStateIndicator.State.STOPPED)
                closeWithAutoDispose()
            }

            override fun sessionResumed() = runInEdt {
                myDebugStateIndicator.setState(DebugSessionStateIndicator.State.RESUMED)
                disableApplyFilterButton()
                myDataFetcher.setCanInteractWithTableSource(false)
            }

            override fun sessionPaused() = runInEdt {
                myDebugStateIndicator.setState(DebugSessionStateIndicator.State.PAUSED)
                updateApplyFilterButtonState()
                myDataFetcher.setCanInteractWithTableSource(true)
            }

            override fun stackFrameChanged() = runInEdt {
                if (tableSourceRef.context.dataSourceInfo.filterable) {
                    myFilterInput?.setSourcePosition(tableSourceRef.context.frameAccessor.getCurrentSourcePosition())
                }
            }
        }
        project.service<FrameAccessorManager>().registerLifecycleListener(
            tableSourceRef.context.frameAccessor,
            sessionLifecycleListener,
            disposable,
        )


        init()

        updateTableModel(tableSourceRef.initialTableStructure)
    }

    override fun filterApplied(
        filterResult: FilterResult?,
        exception: TableSourceException?
    ) {
        if (exception == null) {
            updateTableModel(filterResult!!.tableStructure, filterResult.sort)

            myFilterInput?.saveInputInHistory()
        } else {
            val data = exception.data
            when (data.errorKind) {
                TableSourceErrorKind.UNSUPPORTED_FILTER_FRAME_TYPE -> {
                    myFilterInput?.showErrorMessage("Type of filter frame is not supported: ${data.info}")
                }

                TableSourceErrorKind.EVAL_FAILED -> {
                    myFilterInput?.showErrorMessage("Could not evaluate filter: ${data.cause}")
                }

                else -> {
                    myFilterInput?.showErrorMessage("Could not apply filter: ${data.cause}")
                }
            }
        }

        updateApplyFilterButtonState()
    }

    private fun createFilterInput(
        tableSourceRef: IPyTableSourceRef,
        pluginSettings: ApplicationSettingsService.MyState,
        ): AbstractFilterInput {
        val filterInputCompletionProvider = MyFilterInputCompletionProvider(
            tableSourceRef,
            pluginSettings.filterInputWithRuntimeCodeCompletionInPythonConsole,
            pluginSettings.filterInputWithAdditionCodeCompletion,
        )

        val editorsProvider = FilterInputEditorsProvider(project) { psiFile ->
            Disposer.register(disposable) {
                COMPLETION_PROVIDER_KEY.set(psiFile, null)
                TABLE_SOURCE_CONTEXT_KEY.set(psiFile, null)
            }
            COMPLETION_PROVIDER_KEY.set(psiFile, filterInputCompletionProvider)
            TABLE_SOURCE_CONTEXT_KEY.set(psiFile, tableSourceRef.context)
        }.also { provider ->
            val identifierType = tableSourceRef.context.syntheticIdentifierType
            project.service<SyntheticDataFramePsiRefProvider>()
                .computeIfAbsent(identifierType) { provider.createSyntheticIdentifierPointer(identifierType) }
        }

        return FilterInputFactory.createComponent(project, editorsProvider).also {
            Disposer.register(disposable, it)
            it.setChangedListener(::updateApplyFilterButtonState)
            Disposer.register(disposable) { it.setChangedListener(null) }
        }
    }

    private fun updateTableModel(tableStructure: TableStructure, sort: SortConfig? = null) {
        myDataFrameModel?.let { Disposer.dispose(it) }

        AsyncDataFrameModel(
            myDataFetcher,
            tableStructure,
            tableSourceRef.context.dataSourceInfo.sortable,
            tableSourceRef.context.dataSourceInfo.hasIndexLabels,
        ).let { model ->
            Disposer.register(disposable, model)
            myDataFrameModel = model

            myTable.setDataFrameModel(model)
            myTable.getValuesTable().let { valuesTable ->
                valuesTable.rowSorter?.let { sorter ->
                    if (sort != null) {
                        sorter.sortKeys = sort.byIndex.mapIndexed { colIndex, index ->
                            SortKey(
                                valuesTable.convertColumnIndexToModel(colIndex),
                                if (sort.ascending[index]) SortOrder.ASCENDING else SortOrder.DESCENDING,
                            )
                        }
                    }
                    // add listener after we applied the initial sort state
                    val rowSortListener = RowSorterListener { event ->
                        myDataFetcher.setSorting(SortConfig.fromSortKeys(event.source.sortKeys))
                    }
                    sorter.addRowSorterListener(rowSortListener)
                    Disposer.register(model) { sorter.removeRowSorterListener(rowSortListener) }
                }
            }
        }

        updateFooterLabel(tableStructure)

        // assign focus to the DataFrame table
        // -> allows immediately to use key bindings of the table like sort/scroll/etc.
        IdeFocusManager.getInstance(project).requestFocus(preferredFocusedComponent, true)
    }

    override fun dispose() {
        myTable.setDataFrameModel(EmptyDataFrameModel())
        super.dispose()
    }

    fun closeWithAutoDispose() {
        close(CANCEL_EXIT_CODE)
    }

    private fun disableApplyFilterButton() {
        myOKAction.isEnabled = false
    }

    private fun updateApplyFilterButtonState() {
        if (tableSourceRef.context.dataSourceInfo.filterable) {
            myFilterInput?.let { input ->
                myOKAction.isEnabled = myDataFetcher.getLastAppliedFilter() != input.getText()
                if (!myOKAction.isEnabled) {
                    // clear potential error message
                    input.hideErrorMessage()
                }
            }
        }
    }

    override fun doOKAction() {
        disableApplyFilterButton()
        myFilterInput?.hideErrorMessage()

        val filterExpr = myFilterInput?.getText() ?: ""
        val syntheticIdentifierUsed =
            if (filterExpr.isNotEmpty() && tableSourceRef.context.frameAccessor.syntheticIdentifierCanBeUsed()) myFilterInput?.containsSyntheticIdentifier()
                ?: false
            else false

        myDataFetcher.setFilter(FilterConfig(filterExpr, syntheticIdentifierUsed))
    }

    override fun setErrorText(text: String?, component: JComponent?) {
        super.setErrorText(if (text == null) null else StringUtil.escapeXmlEntities(text), component)
    }

    override fun createActions(): Array<Action> {
        val actions = super.createActions()
        if (!tableSourceRef.context.dataSourceInfo.filterable) {
            return actions.filter { it != myOKAction }.toTypedArray()
        }
        return actions
    }

    private fun updateFooterLabel(tableStructure: TableStructure) {
        myTableFooterLabel.text = tableStructure.let {
            NumberFormat.getInstance().let { nf ->
                val rowsCounter =
                    if (it.rowsCount != it.orgRowsCount) {
                        "${nf.format(it.rowsCount)} of ${nf.format(it.orgRowsCount)}"
                    } else nf.format(it.rowsCount)
                val colsCounter =
                    if (it.columnsCount != it.orgColumnsCount) {
                        "${nf.format(it.columnsCount)} of ${nf.format(it.orgColumnsCount)}"
                    } else nf.format(it.columnsCount)
                "$rowsCounter rows x $colsCounter columns"
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        myCellStylingModeComboBox.maximumSize = myCellStylingModeComboBox.preferredSize

        val tableTopBar = JBBox.createHorizontalBox().apply {
            add(JBLabel("Cell styling:"))
            add(myCellStylingModeComboBox)
            add(JBBox.createHorizontalGlue())
            add(myDebugStateIndicator)
        }
        return FormBuilder.createFormBuilder()
            .addComponent(tableTopBar)
            .addComponent(myTable)
            .addComponent(myTableFooterLabel)
            .addVerticalGap(10)
            .apply {
                if (tableSourceRef.context.dataSourceInfo.filterable) {
                    this.addComponent(myFilterInput!!.getMainComponent())
                }
            }
            .panel
    }

    override fun getDimensionServiceKey(): String {
        return "#cms.rendner.intellij.dataframe.viewer.python.actions.ShowStyledDataFrameAction"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return myTable.getPreferredFocusedComponent()
    }
}
