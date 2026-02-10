package utils

import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class ExecHelper @Inject constructor(private val execOps: ExecOperations) {

    private fun resolveDockerExecutable(): String {
        // return "docker" // doesn't work on Mac after migrating to ExecOperations (reason unknown)
        return ProcessBuilder("which", "docker")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText().trim()
            .ifEmpty { "docker" }
    }

    fun exec(configure: ExecSpec.() -> Unit) {
        execOps.exec(configure)
    }

    fun dockerExec(vararg args: Any, configure: ExecSpec.() -> Unit = {}) {
        execOps.exec {
            configure()
            commandLine(resolveDockerExecutable(), *args)
        }
    }

    fun dockerImageExists(imageName: String): Boolean {
        val imageId = ByteArrayOutputStream().use { outputStream ->
            dockerExec("images", "-q", imageName) {
                standardOutput = outputStream
                isIgnoreExitValue = true
            }
            outputStream.toString().trim()
        }
        return imageId.isNotEmpty()
    }
}
