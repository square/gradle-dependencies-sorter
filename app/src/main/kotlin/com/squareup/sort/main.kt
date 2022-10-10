package com.squareup.sort

import com.squareup.log.DelegatingLogger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

fun main(vararg args: String) {
  val fileSystem = FileSystems.getDefault()

  val exitCode = logger(fileSystem).use { logger ->
    val cli = CommandLine(
      SortCommand(
        logger = logger,
        fileSystem = fileSystem
      )
    )
    cli.execute(*args)
  }
  exitProcess(exitCode)
}

private fun logger(fileSystem: FileSystem): DelegatingLogger {
  val logDir = fileSystem.getPath(
    System.getProperty("java.io.tmpdir"),
    "dependencies-sorter"
  ).createDirectories()
  val logFile = Files.createFile(logDir.resolve("${Instant.now()}.log"))

  return DelegatingLogger(
    delegate = LoggerFactory.getLogger("Sorter"),
    file = logFile
  )
}
