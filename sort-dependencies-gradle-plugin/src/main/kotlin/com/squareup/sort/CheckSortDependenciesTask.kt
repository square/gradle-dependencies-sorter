package com.squareup.sort

import com.squareup.sort.internal.VersionNumber
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CacheableTask
abstract class CheckSortDependenciesTask @Inject constructor(
  private val execOps: ExecOperations
) : BaseSortDependenciesTask() {

  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Checks if the dependencies block in a Gradle build script is sorted"
  }

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val buildScript: RegularFileProperty

  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction
  fun action() {
    val buildScript = buildScript.get().asFile.absolutePath
    val verbose = verbose.getOrElse(false)

    val output = output.get().asFile
    output.delete()

    logger.info("Checking if '$buildScript' is sorted.")

    val version = VersionNumber.parse(version.get().removeSuffix("-SNAPSHOT"))

    val result = execOps.exec { execSpec ->
      execSpec.setIgnoreExitValue(true)
      execSpec.commandLine = listOf(
        "java",
        "-cp", sortProgram.asPath,
        "com.squareup.sort.MainKt",
        buildScript,
        "--mode", "check"
      )

      // Not really intended to be user-specified
      if (version > VersionNumber.parse("0.8")) {
        execSpec.args("--context", "gradle")
      }

      if (verbose) {
        if (version < VersionNumber.parse("0.3")) {
          logger.warn("--verbose specified by version < 0.3. Ignoring flag.")
        } else {
          execSpec.args("--verbose")
        }
      }
    }

    val resultText = when (result.exitValue) {
      0 -> "Dependencies are correctly sorted."
      2 -> "Dependencies are not correctly sorted."
      3 -> "There were parse errors."
      else -> "The exit code ${result.exitValue} is not known."
    }

    output.writeText(resultText)

    if (result.exitValue == 2) {
      throw VerificationException("Dependencies are not correctly sorted.")
    } else if (result.exitValue == 3) {
      throw RuntimeException("There were parse errors.")
    } else if (result.exitValue > 0) {
      throw RuntimeException("The command failed with exit code ${result.exitValue}.")
    }
  }
}
