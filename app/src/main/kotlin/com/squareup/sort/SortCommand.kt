package com.squareup.sort

import com.squareup.log.DelegatingLogger
import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.Status.NOT_SORTED
import com.squareup.sort.Status.NO_BUILD_SCRIPTS_FOUND
import com.squareup.sort.Status.NO_PATH_PASSED
import com.squareup.sort.Status.PARSE_ERROR
import com.squareup.sort.Status.SUCCESS
import com.squareup.sort.Status.UNKNOWN_MODE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.Command
import picocli.CommandLine.HelpCommand
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Callable
import kotlin.io.path.createTempFile
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/**
 * Parent command or entry point into the dependencies-sorter. Not implementing `Callable` nor
 * `Runnable` would indicate that a subcommand _must_ be specified.
 *
 * @see <a href="https://picocli.info/#_required_subcommands">Picocli: Required subcommands</a>
 */
@Command(
  name = "sort",
  mixinStandardHelpOptions = true,
  versionProvider = ResourceVersionProvider::class,
  description = ["Sorts dependencies"],
  subcommands = [
    HelpCommand::class
  ]
)
class SortCommand(
  private val fileSystem: FileSystem = FileSystems.getDefault(),
  private val buildFileFinder: BuildDotGradleFinder.Factory = object : BuildDotGradleFinder.Factory {}
) : Callable<Int> {

  @Option(
    names = ["-v", "--verbose"],
    description = [
      "Verbose mode. All logs are printed."
    ],
    defaultValue = "false"
  )
  var verbose: Boolean = false

  @Option(
    names = ["--skip-hidden-and-build-dirs"],
    description = [
      "Flag to control whether file tree walking looks in build and hidden directories. True by default."
    ],
    defaultValue = "true"
  )
  var skipHiddenAndBuildDirs: Boolean = true

  @Option(
    names = ["-m", "--mode"],
    description = [
      "Mode: [sort, check]. Defaults to 'sort'. Check will report if a file is already sorted"
    ],
    defaultValue = "sort"
  )
  lateinit var mode: String

  @Parameters(
    index = "0..*",
    description = ["Path(s) to sort. Required."],
  )
  lateinit var paths: List<String>

  override fun call(): Int {
    // Use `use()` to ensure the logger is closed + dumps any close-time diagnostics
    return logger(!verbose).use(::callWithLogger)
  }

  private fun callWithLogger(logger: DelegatingLogger): Int {
    if (!this::paths.isInitialized) {
      logger.error("No paths were passed. See 'help' for usage information.")
      return NO_PATH_PASSED.value
    }

    val pwd = fileSystem.getPath(".").toAbsolutePath().normalize()
    logger.info("Sorting build.gradle(.kts) scripts in the following paths: ${paths.joinToString()}")

    val start = System.currentTimeMillis()
    val filesToSort = buildFileFinder.of(
      root = pwd,
      searchPaths = paths,
      skipHiddenAndBuildDirs = skipHiddenAndBuildDirs
    ).buildDotGradles
    val findFileTime = System.currentTimeMillis()

    if (filesToSort.isEmpty()) {
      logger.warn("No build.gradle(.kts) scripts found.")
      return SUCCESS.value
    } else {
      logger.info(
        "It took ${findFileTime - start} ms to find ${filesToSort.size} build scripts."
      )
    }

    val status = when (mode) {
      "sort" -> sort(filesToSort, findFileTime, logger)
      "check" -> check(filesToSort, findFileTime, pwd, logger)
      else -> UNKNOWN_MODE
    }

    return status.value
  }

  private fun sort(
    filesToSort: Set<Path>,
    findFileTime: Long,
    logger: Logger,
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

    return if (parseErrorCount == 0) SUCCESS else PARSE_ERROR
  }

  private fun check(
    filesToSort: Set<Path>,
    findFileTime: Long,
    pwd: Path,
    logger: Logger,
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

    return if (success) {
      SUCCESS
    } else if (parseErrorCount > 0) {
      PARSE_ERROR
    } else {
      NOT_SORTED
    }
  }
}

enum class Status(val value: Int) {
  SUCCESS(0),
  NO_PATH_PASSED(1),
  NO_BUILD_SCRIPTS_FOUND(2),
  NOT_SORTED(3),
  UNKNOWN_MODE(4),
  PARSE_ERROR(5),
  ;
}

private fun logger(quiet: Boolean): DelegatingLogger {
  return DelegatingLogger(
    delegate = LoggerFactory.getLogger("Sorter"),
    file = createTempFile(),
    quiet = quiet,
  )
}
