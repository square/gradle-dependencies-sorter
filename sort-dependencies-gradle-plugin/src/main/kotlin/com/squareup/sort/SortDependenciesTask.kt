package com.squareup.sort

import com.squareup.sort.internal.VersionNumber
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
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
  //
  // @Incremental, so that InputChanges tells the action whether this is the only input that changed; see action().
  @get:Incremental
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val buildScript: RegularFileProperty

  // Gradle only considers a task up-to-date if it has outputs. This one holds a hash of the build script as the last
  // sort left it; see action().
  @get:OutputFile
  abstract val output: RegularFileProperty

  @TaskAction fun action(inputChanges: InputChanges) {
    val buildScriptFile = buildScript.get().asFile
    val buildScript = buildScriptFile.absolutePath
    val verbose = verbose.getOrElse(false)
    val insertBlankLines = insertBlankLines.getOrElse(true)
    val output = output.get().asFile

    // Gradle snapshots a task's inputs before its action runs, so it records the build script as it was before the
    // sort. When the sort rewrites the script, the next build sees a changed input and runs this task again, although
    // the script is exactly what the sort produced. That run would start the sorter's JVM only to find nothing to do.
    //
    // Gradle runs this task incrementally only when the build script is the sole change since the last execution, and
    // that execution succeeded. Anything else forces a full run: a changed option or sorter classpath, a different
    // plugin version, a failed previous run, or a modified output. If, on top of that, the build script still hashes
    // to what the last sort left, there is nothing to sort.
    if (inputChanges.isIncremental && output.readText() == hash(buildScriptFile)) {
      logger.info("'$buildScript' is unchanged since it was last sorted.")
      return
    }

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

    output.writeText(hash(buildScriptFile))
  }

  private fun hash(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(file.inputStream(), digest).use { it.copyTo(OutputStream.nullOutputStream()) }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun MutableList<String>.option(name: String, value: String) {
    add(name)
    add(value)
  }
}
