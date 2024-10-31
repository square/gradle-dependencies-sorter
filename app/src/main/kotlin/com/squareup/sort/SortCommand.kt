package com.squareup.sort

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import com.squareup.log.DelegatingLogger
import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.Status.NOT_SORTED
import com.squareup.sort.Status.PARSE_ERROR
import com.squareup.sort.Status.SUCCESS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.createTempFile
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/** Parent command or entry point into the dependencies-sorter. */
class SortCommand(
  private val fileSystem: FileSystem = FileSystems.getDefault(),
  private val buildFileFinder: BuildDotGradleFinder.Factory = object : BuildDotGradleFinder.Factory {}
) : CliktCommand(
  name = "sort",
  help = "Sorts dependencies",
) {

  init {
    context {
      helpFormatter = { context ->
        MordantHelpFormatter(
          context = context,
          showDefaultValues = true,
          showRequiredTag = true,
        )
      }
    }
  }

  val paths: List<Path> by argument(help = "Path(s) to sort. Required.")
    .path(mustExist = false, canBeDir = true, canBeFile = true)
    .multiple(required = true)

  private val verbose by option(
    "-v", "--verbose",
    help = "Verbose mode. All logs are printed."
  ).flag("--quiet", default = false)

  private val skipHiddenAndBuildDirs by option(
    "--skip-hidden-and-build-dirs",
    help = "Flag to control whether file tree walking looks in build and hidden directories. True by default.",
  ).flag("--no-skip-hidden-and-build-dirs", default = true)

  val mode by option(
    "-m", "--mode",
    help = "Mode: [sort, check]. Defaults to 'sort'. Check will report if a file is already sorted."
  ).enum<Mode>().default(Mode.SORT)

  val context by option(
    "--context",
    help = "Context: [cli, gradle]. Defaults to 'cli'. Used for more helpful error messages.",
  ).enum<Context>().default(Context.CLI)

  override fun run() {
    // Use `use()` to ensure the logger is closed + dumps any close-time diagnostics
    logger(!verbose).use(::callWithLogger)
  }

  private fun callWithLogger(logger: DelegatingLogger) {
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
      throw ProgramResult(SUCCESS.value)
    } else {
      logger.info(
        "It took ${findFileTime - start} ms to find ${filesToSort.size} build scripts."
      )
    }

    val status = when (mode) {
      Mode.SORT -> sort(filesToSort, findFileTime, logger)
      Mode.CHECK -> check(filesToSort, findFileTime, pwd, logger)
    }

    throw ProgramResult(status.value)
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
        val newContent = Sorter.of(file).rewritten()
        file.writeText(newContent, Charsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)
        logger.trace("Successfully sorted: ${file.pathString} ")
        successCount++
      } catch (e: BuildScriptParseException) {
        logger.error("Parsing error: ${file.pathString} \n${e.localizedMessage}")
        parseErrorCount++
      } catch (e: IllegalStateException) {
        logger.error("Parsing error: ${file.pathString} \n${e.localizedMessage}")
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
        val sorter = Sorter.of(file)
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
          logger.error("Parsing error: ${file.pathString} \n${error.localizedMessage}")
          parseErrorCount++
        }
      } catch (t: Throwable) {
        logger.error("Parsing error: ${file.pathString}")
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

        when (context) {
          Context.GRADLE -> appendLine("./gradlew sortDependencies")

          Context.CLI -> {
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
        }
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

enum class Context {
  CLI,
  GRADLE,
  ;
}

enum class Mode {
  SORT,
  CHECK,
  ;
}

enum class Status(val value: Int) {
  SUCCESS(0),
  NO_BUILD_SCRIPTS_FOUND(1),
  NOT_SORTED(2),
  PARSE_ERROR(3),
  ;
}

private fun logger(quiet: Boolean): DelegatingLogger {
  return DelegatingLogger(
    delegate = LoggerFactory.getLogger("Sorter"),
    file = createTempFile(),
    quiet = quiet,
  )
}
