/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.actions

import cms.rendner.intellij.dataframe.viewer.components.DataFrameViewerDialog
import cms.rendner.intellij.dataframe.viewer.components.filter.IDataFrameColumnNameContributor
import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterInputCompletionContributor
import cms.rendner.intellij.dataframe.viewer.components.filter.SyntheticDataFrameIdentifier
import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.CompletionVariant
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.pycharm.debugProcessIsTerminated
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.services.ParentDisposableService
import cms.rendner.intellij.dataframe.viewer.services.SyntheticDataFramePsiRefProvider
import cms.rendner.intellij.dataframe.viewer.settings.ApplicationSettingsService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.PlatformIcons
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.console.completion.PydevConsoleElement
import com.jetbrains.python.console.pydev.ConsoleCommunication
import com.jetbrains.python.console.pydev.IToken
import com.jetbrains.python.console.pydev.PyCodeCompletionImages
import com.jetbrains.python.debugger.*
import com.jetbrains.python.psi.*

private class MySelectCodeProviderAction(
    val provider: ITableSourceCodeProvider,
    val transformHint: DataSourceTransformHint?,
) : AbstractShowViewerAction() {
    override fun getApplicableCodeProvider(event: AnActionEvent, dataSource: PyDebugValue) = provider
    override fun getDataSourceTransformHint() = transformHint
    override fun update(e: AnActionEvent) {
        e.presentation.text = provider.getDataFrameLibrary().moduleName
    }
}

/**
 * Opens a popup in case more than one matching [ITableSourceCodeProvider] are available to transform a data source
 * into a viewable data frame. The user can select the preferred code provider from the popup.
 */
abstract class BaseShowViewerAction : AbstractShowViewerAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val dataSource = getSelectedDebugValue(event) ?: return

        val providers = getApplicableCodeProviders(event, dataSource)
        if (providers.size > 1) {
            runInEdt { showSelectCodeProviderPopup(event, providers) }
            return
        }

        super.actionPerformed(event)
    }

    protected abstract fun getApplicableCodeProviders(event: AnActionEvent, dataSource: PyDebugValue): List<ITableSourceCodeProvider>

    final override fun getApplicableCodeProvider(event: AnActionEvent, dataSource: PyDebugValue): ITableSourceCodeProvider? {
        return getApplicableCodeProviders(event, dataSource).firstOrNull()
    }

    private fun showSelectCodeProviderPopup(event: AnActionEvent, providers: List<ITableSourceCodeProvider>) {
        val transformHint = getDataSourceTransformHint()
        JBPopupFactory.getInstance().createActionGroupPopup(
            "Select DataFrame Library",
            DefaultActionGroup(providers.map { MySelectCodeProviderAction(it, transformHint) }),
            event.dataContext,
            JBPopupFactory.ActionSelectionAid.NUMBERING,
            false,
            null,
        ).also {
            event.project?.let { p -> it.showCenteredInCurrentWindow(p) } ?: it.showInBestPositionFor(event.dataContext)
        }
    }
}

/**
 * Opens the "DataFrameViewerDialog" from the PyCharm debugger.
 */
abstract class AbstractShowViewerAction : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    protected abstract fun getApplicableCodeProvider(event: AnActionEvent, dataSource: PyDebugValue): ITableSourceCodeProvider?

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (project.isDisposed) return
        val dataSource = getSelectedDebugValue(event) ?: return

        val parentDisposable = project.service<ParentDisposableService>()
        val evaluator = dataSource.toPluginType().evaluator
        val codeProvider = getApplicableCodeProvider(event, dataSource)
            ?: throw IllegalStateException("No codeProvider found for ${dataSource.qualifiedType}")

        BackgroundTaskUtil.executeOnPooledThread(parentDisposable) {
            val dataSourceInfo = try {
                PythonPluginCodeInjector.injectIfRequired(evaluator, codeProvider)
                codeProvider.createSourceInfo(dataSource.toValueEvalExpr(), evaluator)
            } catch (ex: Throwable) {
                ErrorNotification("Initialize plugin code failed", ex.localizedMessage ?: "", ex).notify(project)
                return@executeOnPooledThread
            }

            runInEdt {
                if (dataSource.frameAccessor.debugProcessIsTerminated() || parentDisposable.isDisposed || project.isDisposed) return@runInEdt
                val settings = ApplicationSettingsService.instance.state
                val completionContributor = MyFilterInputCompletionContributor(
                    dataSource.frameAccessor,
                    codeProvider.getDataFrameLibrary(),
                    settings.filterInputWithRuntimeCodeCompletionInPythonConsole,
                    settings.filterInputWithAdditionCodeCompletion,
                )
                DataFrameViewerDialog(
                    project,
                    evaluator,
                    dataSourceInfo,
                    completionContributor,
                    getDataSourceTransformHint()
                ).apply {
                    Disposer.register(parentDisposable, disposable)
                    Disposer.register(
                        disposable,
                        MyDebugSessionListener(dataSource.frameAccessor, this, completionContributor),
                    )
                    createTableModel()
                    show()
                }
            }
        }
    }

    protected open fun getDataSourceTransformHint(): DataSourceTransformHint? = null

    protected fun getSelectedDebugValue(event: AnActionEvent): PyDebugValue? {
        return XDebuggerUtil.getInstance().getValueContainer(event.dataContext)?.let {
            if (it is PyDebugValue) it else null
        }
    }
}

private class MyDebugSessionListener(
    frameAccessor: PyFrameAccessor,
    delegate: DataFrameViewerDialog,
    private val filterInputCompletionContributor: MyFilterInputCompletionContributor,
): XDebugSessionListener, PyFrameListener, Disposable {

    // Props are nullable to free circular references on dispose
    // ("PyFrameAccessor" has no "removeFrameListener")
    // Use topic if plugin is compatible with 2023.3(?)
    private var frameAccessor: PyFrameAccessor? = frameAccessor
    private var delegate: DataFrameViewerDialog? = delegate

    init {
        if (frameAccessor is IPyDebugProcess) {
            delegate.setFilterSourcePosition(frameAccessor.session.currentPosition)
            frameAccessor.session.addSessionListener(this)
        } else {
            frameAccessor.addFrameListener(this)
        }
        updateProvideSyntheticIdentifierFlag()
    }

    override fun frameChanged() = stackFrameChanged()

    override fun sessionPaused() = runInEdt {
        delegate?.sessionPaused()
    }

    override fun sessionResumed() = runInEdt {
        delegate?.sessionResumed()
    }

    override fun sessionStopped() = runInEdt {
        delegate?.sessionStopped()
    }

    override fun sessionStopped(communication: PyFrameAccessor?) = runInEdt {
        if (communication == null || communication == frameAccessor) {
            delegate?.sessionStopped()
        }
    }

    override fun stackFrameChanged() {
        updateProvideSyntheticIdentifierFlag()
        runInEdt {
            frameAccessor?.let {
                if (it is IPyDebugProcess) {
                    delegate?.setFilterSourcePosition(it.session.currentPosition)
                }
            }

            delegate?.stackFrameChanged()
        }
    }

    override fun beforeSessionResume() = runInEdt {
        delegate?.beforeSessionResume()
    }

    override fun dispose() {
        frameAccessor?.let {
            if (it is IPyDebugProcess) {
                it.session.removeSessionListener(this)
            }
        }

        frameAccessor = null
        delegate = null
    }

    private fun updateProvideSyntheticIdentifierFlag() {
        delegate?.disposable?.let { disposable ->
            BackgroundTaskUtil.executeOnPooledThread(disposable) {
                frameAccessor?.let { accessor ->
                    try {
                        val result = accessor.evaluate(SyntheticDataFrameIdentifier.NAME, false, false)
                        val nameIsNotDefined =
                            result.isErrorOnEval && result.qualifiedType == PythonQualifiedTypes.NameError
                        filterInputCompletionContributor.provideSyntheticIdentifier = nameIsNotDefined
                    } catch (ex: PyDebuggerException) {
                        var nameIsNotDefined = false
                        ex.message?.let {
                            nameIsNotDefined = it.contains(PythonQualifiedTypes.NameError.split(".").last())
                        }
                        filterInputCompletionContributor.provideSyntheticIdentifier = nameIsNotDefined
                    }
                }
            }
        }
    }
}

private class MyFilterInputCompletionContributor(
    private val frameAccessor: PyFrameAccessor,
    private val syntheticIdentifierType: DataFrameLibrary,
    private val withPythonConsoleCodeCompletion: Boolean,
    private val withPluginCodeCompletion: Boolean,
): IFilterInputCompletionContributor {

    private data class ColNameCompletionInfo(
        val completableText: String?,
        val isInsideString: Boolean,
        private val result: CompletionResultSet,
        ) {
        private var addedVariants: Int = 0

        fun addVariants(variants: List<ICompletionVariant>) {
            for (variant in variants) {
                if (variant is NestedCompletionVariant) continue // todo
                if (variant is CompletionVariant) {
                    if (isInsideString && variant.fqType != PythonQualifiedTypes.STR) continue
                    if (!isInsideString && variant.fqType == PythonQualifiedTypes.STR && completableText != null) continue
                    addElement(
                        if (variant.fqType == PythonQualifiedTypes.STR && !isInsideString) "\"${variant.value}\""
                        else variant.value
                    )
                }
            }
        }

        private fun addElement(text: String) {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder
                        .create(text)
                        .withTypeText("column name")
                        .withIcon(PlatformIcons.PROPERTY_ICON),
                    1001.0 + addedVariants,
                    )
            )
            addedVariants++
        }
    }

    @Volatile
    var provideSyntheticIdentifier: Boolean = false

    @Volatile
    private var myColumnNameContributor: IDataFrameColumnNameContributor? = null

    override fun getSyntheticIdentifierType() = syntheticIdentifierType
    override fun isSyntheticIdentifierEnabled(): Boolean = provideSyntheticIdentifier

    override fun setColumnNameContributor(nameContributor: IDataFrameColumnNameContributor?) {
        myColumnNameContributor = nameContributor
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        addSyntheticIdentifier(parameters, result)
        addExternalCompletionVariants(parameters, result)
    }

    private fun addSyntheticIdentifier(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!provideSyntheticIdentifier) return
        val refExpr = parameters.position.parent as? PyReferenceExpression ?: return
        if (refExpr.isQualified) return
        if (!result.prefixMatcher.prefixMatches(SyntheticDataFrameIdentifier.NAME)) return

        parameters.position.project.service<SyntheticDataFramePsiRefProvider>().getPointer(syntheticIdentifierType)?.let {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder
                        .create(it, SyntheticDataFrameIdentifier.NAME)
                        .withItemTextForeground(JBColor.GRAY)
                        .withTypeText("<synthetic>", true)
                        .withIcon(PlatformIcons.VARIABLE_ICON),
                    1000.0,
                )
            )
        }
    }

    private fun addExternalCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        addPandasDataFrameColumnNames(parameters, result)
        addRuntimeCompletionVariantsForConsole(parameters, result)
    }

    private fun addPandasDataFrameColumnNames(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!withPluginCodeCompletion) return
        if (syntheticIdentifierType != DataFrameLibrary.PANDAS) return
        val columnNameContributor = myColumnNameContributor ?: return

        val subscriptionExpr = PsiTreeUtil.getParentOfType(parameters.position, PySubscriptionExpression::class.java) ?: return
        val refExpr = subscriptionExpr.operand as? PyReferenceExpression ?: return
        // ignore something like "df.loc[<caret>]"
        if (refExpr.isQualified) return

        var indexExpr = subscriptionExpr.indexExpression ?: return

        if (indexExpr is PyListLiteralExpression) {
            // df[[<caret>]]
            if (indexExpr.isEmpty) return
            val elements =  indexExpr.elements
            if (elements.size > 1) return
            indexExpr = elements[0]
        }

        val completionInfo = getColNameCompletionInfo(indexExpr, parameters, result) ?: return
        val identifier = refExpr.text
        val isSyntheticIdentifier = provideSyntheticIdentifier && identifier == SyntheticDataFrameIdentifier.NAME

        ProgressManager.checkCanceled()
        val variants = columnNameContributor.getCompletionVariants(identifier, isSyntheticIdentifier)
        ProgressManager.checkCanceled()

        completionInfo.addVariants(variants)
    }

    private fun getColNameCompletionInfo(
        indexExpr: PyExpression,
        parameters: CompletionParameters,
        result: CompletionResultSet,
        ): ColNameCompletionInfo? {
        if (indexExpr is PyReferenceExpression) {
            if (indexExpr.text == CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED) {
                return ColNameCompletionInfo(null, false, result)
            }
        }
        else if (indexExpr is PyStringLiteralExpression) {
            return ColNameCompletionInfo(
                indexExpr.text.replace(CompletionInitializationContext.DUMMY_IDENTIFIER, ""),
                true,
                result,
            )
        }
        else if (indexExpr is PyNumericLiteralExpression && indexExpr.isIntegerLiteral) {
            val prefixLength = parameters.offset - indexExpr.getTextRange().startOffset
            val prefix = indexExpr.text.substring(0, prefixLength)

            return ColNameCompletionInfo(
                indexExpr.text,
                false,
                result.withPrefixMatcher(prefix),
            )
        }
        return null
    }

    private fun addRuntimeCompletionVariantsForConsole(parameters: CompletionParameters, result: CompletionResultSet) {
        // The console based "frameAccessor" doesn't provide a sourcePosition which could be used for code completion.
        // Instead, the data is fetched from the underlying Python process.
        if (!withPythonConsoleCodeCompletion || frameAccessor !is ConsoleCommunication) return

        val refExpr = parameters.position.parent as? PyReferenceExpression ?: return
        val qName = refExpr.asQualifiedName() ?: return
        val actToken = qName.toString().replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")

        // don't return if actToken is only "_df" instead of "_df."
        // because there could be other variable names which start with "_df"
        if (provideSyntheticIdentifier && actToken.startsWith("${SyntheticDataFrameIdentifier.NAME}.")) return

        val psiManager = parameters.position.manager

        ProgressManager.checkCanceled()
        val variants = frameAccessor.getCompletions(actToken, actToken)
        ProgressManager.checkCanceled()

        variants.forEachIndexed { index, it ->
            var builder = LookupElementBuilder
                .create(PydevConsoleElement(psiManager, it.name, it.description))
                .withIcon(PyCodeCompletionImages.getImageForType(it.type))

            if (it.args.isNotBlank()) {
                builder = builder.withTailText(it.args)
            }
            if (it.type == IToken.TYPE_FUNCTION || it.args.endsWith(")")) {
                builder = builder.withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS)
            }

            result.addElement(PrioritizedLookupElement.withPriority(builder, 100.0 + index))
        }
    }
}