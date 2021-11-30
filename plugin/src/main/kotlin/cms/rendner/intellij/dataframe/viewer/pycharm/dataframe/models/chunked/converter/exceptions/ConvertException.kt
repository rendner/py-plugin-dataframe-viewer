package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter.exceptions

class ConvertException(message: String, private val additionalMessage: String? = null): Exception(message) {

    override fun getLocalizedMessage(): String {
        val msg = super.getLocalizedMessage()
        return additionalMessage?.let {
            if(it.length <= 80) {
                return "$msg $it"
            }
            return "$msg ${it.substring(0, 80)}"
        } ?: msg
    }
}