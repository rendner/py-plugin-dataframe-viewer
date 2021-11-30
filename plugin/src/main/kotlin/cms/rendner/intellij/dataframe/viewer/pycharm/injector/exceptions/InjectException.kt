package cms.rendner.intellij.dataframe.viewer.pycharm.injector.exceptions

import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException

class InjectException(message: String, override val cause: EvaluateException? = null) : Exception(message)