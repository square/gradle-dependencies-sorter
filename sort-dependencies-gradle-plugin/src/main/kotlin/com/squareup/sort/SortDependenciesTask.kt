package com.squareup.sort

import com.squareup.sort.internal.VersionNumber
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.security.MessageDigest
import javax.inject.Inject

abstract class SortDependenciesTask @Inject constructor(
  private val execOps: ExecOperations
) : BaseSortDependenciesTask() {

  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Sorts the dependencies block in a Gradle build script"

    @Suppress("LeakingThis")
    doNotTrackState("This task modifies build scripts in place.")

    // Skip the expensive javaexec when nothing that affects the result has changed since the last
    // successful sort: the build script's content, the sorter's classpath, and the sort options.
    onlyIf { task -> (task as SortDependenciesTask).hasChangedSinceLastSort() }
  }

  // Not really "internal", but the input and the output are the same: this task will potentially modify the build
  // script in place.
  @get:Internal
  abstract val buildScript: RegularFileProperty

  // Not a real output — doNotTrackState disables all tracking. Holds the fingerprint of the
  // inputs of the last successful sort, which the onlyIf check compares against.
  @get:Internal
  abstract val fingerprintFile: RegularFileProperty

  @TaskAction fun action() {
    val buildScript = buildScript.get().asFile.absolutePath
    val verbose = verbose.getOrElse(false)
    val insertBlankLines = insertBlankLines.getOrElse(true)

    logger.info("Sorting '$buildScript'.")

    // A failed sort must not leave the previous fingerprint behind.
    val fingerprintFile = fingerprintFile.get().asFile
    fingerprintFile.delete()

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

    // Fingerprint the sorted result, so the next run is skipped until something changes.
    fingerprintFile.parentFile.mkdirs()
    fingerprintFile.writeText(computeFingerprint())
  }

  private fun hasChangedSinceLastSort(): Boolean {
    val fingerprintFile = fingerprintFile.get().asFile
    return !fingerprintFile.exists() || fingerprintFile.readText() != computeFingerprint()
  }

  private fun computeFingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun update(value: Any) {
      digest.update(value.toString().toByteArray())
      digest.update(0)
    }

    update(version.get())
    update(insertBlankLines.getOrElse(true))
    blocks.getOrElse(emptySet()).sorted().forEach(::update)
    // Covers a changed version or a republished snapshot without hashing every jar on every run.
    sortProgram.files.forEach { file ->
      update(file.absolutePath)
      update(file.length())
      update(file.lastModified())
    }
    digest.update(buildScript.get().asFile.readBytes())

    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun MutableList<String>.option(name: String, value: String) {
    add(name)
    add(value)
  }
}
