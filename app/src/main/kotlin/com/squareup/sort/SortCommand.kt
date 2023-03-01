package com.squareup.sort

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.Status.NOT_SORTED
import com.squareup.sort.Status.NO_BUILD_SCRIPTS_FOUND
import com.squareup.sort.Status.SUCCESS
import org.slf4j.Logger
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/**
 * Parent command or entry point into the dependencies-sorter. Not implementing `Callable` nor
 * `Runnable` would indicate that a subcommand _must_ be specified.
 *
 * @see <a href="https://picocli.info/#_required_subcommands">Picocli: Required subcommands</a>
 */
class SortCommand(
  private val logger: Logger,
  private val fileSystem: FileSystem,
  private val buildFileFinder: BuildDotGradleFinder.Factory = BuildDotGradleFinder.Factory.Default
) : CliktCommand(
  name = "sort",
  help = "Sorts dependencies",
) {

  init {
    context { helpFormatter = CliktHelpFormatter(showDefaultValues = true, showRequiredTag = true) }
  }

  val mode: Mode by option(
    "-m", "--mode",
    help = "Mode: [sort, check]. Check will report if a file is already sorted",
  ).enum<Mode>().default(Mode.SORT)

  val paths: List<String> by argument(help = "Path(s) to sort.")
    .multiple(required = true)

  override fun run() {
    val pwd = fileSystem.getPath(".").toAbsolutePath().normalize()
    logger.info("Sorting build.gradle(.kts) scripts in the following paths: ${paths.joinToString()}")

    val start = System.currentTimeMillis()
    val filesToSort = buildFileFinder.of(pwd, paths).buildDotGradles
    val findFileTime = System.currentTimeMillis()

    if (filesToSort.isEmpty()) {
      logger.error("No build.gradle(.kts) scripts found.")
      throw ProgramResult(NO_BUILD_SCRIPTS_FOUND.value)
    } else {
      logger.info(
        "It took ${findFileTime - start} ms to find ${filesToSort.size} build scripts."
      )
    }

    val status = when (mode) {
      Mode.SORT -> sort(filesToSort, findFileTime)
      Mode.CHECK -> check(filesToSort, findFileTime, pwd)
    }

    if (status != SUCCESS) {
      throw ProgramResult(status.value)
    }
  }

  private fun sort(
    filesToSort: Set<Path>,
    findFileTime: Long
  ): Status {
    // Rewrites every build.gradle it finds
    var successCount = 0
    var parseErrorCount = 0
    var alreadySortedCount = 0
    filesToSort.parallelStream().forEach { file ->
      try {
        val newContent = Sorter.sorterFor(file).rewritten()
        file.writeText(newContent, Charsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)
        logger.trace("Successfully sorted: ${file.pathString} ")
        successCount++
      } catch (e: BuildScriptParseException) {
        logger.warn("Parsing error: ${file.pathString} \n${e.localizedMessage}")
        parseErrorCount++
      } catch (e: IllegalStateException) {
        logger.warn("Parsing error: ${file.pathString} \n${e.localizedMessage}")
        parseErrorCount++
      } catch (_: AlreadyOrderedException) {
        logger.trace("Already ordered: ${file.pathString} ")
        alreadySortedCount++
      }
    }

    logger.info(
      """
        Metrics:
          Successful sorts: $successCount
          Already sorted:   $alreadySortedCount
          Parse errors:     $parseErrorCount

        Sort duration: ${System.currentTimeMillis() - findFileTime} ms.
      """.trimIndent()
    )

    return SUCCESS
  }

  private fun check(
    filesToSort: Set<Path>,
    findFileTime: Long,
    pwd: Path,
  ): Status {
    val notSorted = mutableListOf<Path>()
    var parseErrorCount = 0
    var alreadySortedCount = 0

    filesToSort.parallelStream().forEach { file ->
      try {
        val sorter = Sorter.sorterFor(file)
        if (!sorter.isSorted() && !sorter.hasParseErrors()) {
          logger.trace("Not ordered: ${file.pathString} ")
          notSorted.add(file)
        }
        if (sorter.isSorted()) {
          logger.trace("Already ordered: ${file.pathString} ")
          alreadySortedCount++
        }
        if (sorter.hasParseErrors()) {
          val error = checkNotNull(sorter.getParseError()) { "There must be a parse error." }
          logger.trace("Parsing error: ${file.pathString} \n${error.localizedMessage}")
          parseErrorCount++
        }
      } catch (t: Throwable) {
        logger.trace("Parsing error: ${file.pathString}")
        parseErrorCount++
      }
    }

    val success = notSorted.isEmpty()
    val headline =
      if (success) "Success! No mis-ordered build scripts."
      else "Failed! ${notSorted.size} scripts are not ordered correctly."

    val log = buildString {
      appendLine(headline)
      appendLine("Metrics:")
      appendLine("  Not sorted:     ${notSorted.size}")
      appendLine("  Already sorted: $alreadySortedCount")
      appendLine("  Parse errors:   $parseErrorCount")
      if (!success) {
        appendLine()
        appendLine("Fix by running")
        appendLine(
          notSorted.joinToString(
            prefix = "./scripts/sort ",
            separator = " ",
            transform = {
              // Log relative path of the unsorted file.
              pwd.relativize(it.normalize()).pathString
            },
          )
        )
      }

      appendLine()
      appendLine("Check duration: ${System.currentTimeMillis() - findFileTime} ms.")
    }

    logger.info(log)

    return if (success) SUCCESS else NOT_SORTED
  }
}

enum class Mode {
  SORT,
  CHECK,
}

enum class Status(val value: Int) {
  SUCCESS(0),
  NO_BUILD_SCRIPTS_FOUND(1),
  NOT_SORTED(2),
  ;
}
