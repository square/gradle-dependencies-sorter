package com.squareup.sort

import com.squareup.sort.internal.VersionNumber
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class SortDependenciesTask @Inject constructor(
  private val execOps: ExecOperations
) : BaseSortDependenciesTask() {

  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Sorts the dependencies block in a Gradle build script"

    @Suppress("LeakingThis")
    doNotTrackState("This task modifies build scripts in place.")

    // Skip the expensive javaexec when the build script hasn't been modified since the last sort,
    // using a marker file's timestamp as the reference point.
    @Suppress("LeakingThis")
    onlyIf {
      val marker = markerFile.get().asFile
      !marker.exists() || buildScript.get().asFile.lastModified() > marker.lastModified()
    }
  }

  // Not really "internal", but the input and the output are the same: this task will potentially modify the build
  // script in place.
  @get:Internal
  abstract val buildScript: RegularFileProperty

  // Not a real output — doNotTrackState disables all tracking. This is only used by the onlyIf
  // check to skip re-sorting unchanged build scripts based on the marker's timestamp.
  @get:Internal
  abstract val markerFile: RegularFileProperty

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
        }
      }
    }

    // Touch the marker file so the onlyIf check can skip future runs. writeBytes is used instead
    // of createNewFile because the latter is a no-op when the file already exists.
    markerFile.get().asFile.apply {
      parentFile.mkdirs()
      writeBytes(ByteArray(0))
    }
  }

  private fun MutableList<String>.option(name: String, value: String) {
    add(name)
    add(value)
  }
}
