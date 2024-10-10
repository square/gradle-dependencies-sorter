package com.squareup.sort

import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class SortDependenciesTask @Inject constructor(
  private val execOps: ExecOperations
) : DefaultTask() {

  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Sorts the dependencies block in a Gradle build script"

    @Suppress("LeakingThis")
    doNotTrackState("This task modifies build scripts in place.")
  }

  // Not really "internal", but the input and the output are the same: this task will potentially modify the build
  // script in place.
  @get:Internal
  abstract val buildScript: RegularFileProperty

  @get:Classpath
  @get:InputFiles
  abstract val sortProgram: ConfigurableFileCollection

  /** The app version limits what options we can pass it. */
  @get:Input
  abstract val version: Property<String>

  @get:Input
  abstract val mode: Property<String>

  @get:Optional
  @get:Option(option = "verbose", description = "Enables verbose logging.")
  @get:Input
  abstract val verbose: Property<Boolean>

  @TaskAction fun action() {
    val buildScript = buildScript.get().asFile.absolutePath
    val mode = mode.getOrElse("sort")
    val verbose = verbose.getOrElse(false)

    if (mode != "check" && mode != "sort") {
      throw InvalidUserDataException("Mode must be 'sort' or 'check'. Was '$mode'.")
    }

    logger.info("Sorting '$buildScript' using mode '$mode'.")

    val version = version.get().removeSuffix("-SNAPSHOT").toDouble()

    execOps.javaexec { javaExecSpec ->
      with(javaExecSpec) {
        mainClass.set("com.squareup.sort.MainKt")
        classpath = sortProgram
        args = buildList {
          add(buildScript)
          option("--mode", mode)

          // Not really intended to be user-specified
          if (version > 0.8) {
            option("--context", "gradle")
          }

          if (verbose) {
            if (version < 0.3) {
              logger.warn("--verbose specified by version < 0.3. Ignoring flag.")
            } else {
              add("--verbose")
            }
          }
        }
      }
    }
  }

  private fun MutableList<String>.option(name: String, value: String) {
    add(name)
    add(value)
  }
}
