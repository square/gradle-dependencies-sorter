package com.squareup.sort

import com.squareup.sort.internal.VersionNumber
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Modifies the build script in place, which a cache hit would not")
abstract class SortDependenciesTask @Inject constructor(
  private val execOps: ExecOperations
) : BaseSortDependenciesTask() {

  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Sorts the dependencies block in a Gradle build script"
  }

  // Also modified in place. It is deliberately not declared as an output too: checkSortDependencies reads it, and
  // Gradle would then require that task to order itself against this one.
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val buildScript: RegularFileProperty

  // Gradle only considers a task up-to-date if it has outputs. Nothing reads this one; it stays empty.
  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action() {
    val buildScript = buildScript.get().asFile.absolutePath
    val verbose = verbose.getOrElse(false)
    val insertBlankLines = insertBlankLines.getOrElse(true)

    logger.info("Sorting '$buildScript'.")

    val version = VersionNumber.parse(version.get().removeSuffix("-SNAPSHOT"))

    execOps.javaexec { javaExecSpec ->
      with(javaExecSpec) {
        mainClass.set("com.squareup.sort.MainKt")
        classpath = sortProgram
        args = buildList {
          add(buildScript)
          option("--mode", "sort")

          // Not really intended to be user-specified
          if (version > VersionNumber.parse("0.8")) {
            option("--context", "gradle")
          }

          if (verbose) {
            if (version < VersionNumber.parse("0.3")) {
              logger.warn("--verbose specified by version < 0.3. Ignoring flag.")
            } else {
              add("--verbose")
            }
          }

          if (!insertBlankLines) {
            add("--no-blank-lines")
          }

          blocks.getOrElse(emptySet()).sorted().forEach { block ->
            option("--block", block)
          }
        }
      }
    }

    output.get().asFile.writeText("")
  }

  private fun MutableList<String>.option(name: String, value: String) {
    add(name)
    add(value)
  }
}
