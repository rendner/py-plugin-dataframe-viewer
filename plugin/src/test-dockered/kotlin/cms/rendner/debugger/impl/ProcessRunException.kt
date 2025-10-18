package cms.rendner.debugger.impl

import kotlin.Exception

class ProcessRunException(
    message: String,
    reason: String? = null,
    exitCode: Int? = null,
) : Exception(
    buildString {
        appendLine(message)
        appendLine("Exit code: ${exitCode ?: "N/A"}")
        appendLine("Reason: ${reason ?: "N/A"}")
        append(reason)
    }
) {
    companion object {
        fun throwIfExitedUnexpected(process: Process, message: String) {
            if (!process.isAlive && process.exitValue() != 0) {
                var reason = "?"
                if (process.errorStream.available() > 0) {
                    process.errorStream.bufferedReader().use { errReader ->
                        reason = errReader.readText()
                    }
                } else if (process.inputStream.available() > 0) {
                    process.inputStream.bufferedReader().use { inReader ->
                        reason = inReader.readText()
                    }
                }
                throw ProcessRunException(message, reason, process.exitValue())
            }
        }
    }
}