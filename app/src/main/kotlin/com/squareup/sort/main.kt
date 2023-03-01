package com.squareup.sort

import com.squareup.log.DelegatingLogger
import org.slf4j.LoggerFactory
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

fun main(vararg args: String) {
  val fileSystem = FileSystems.getDefault()

  logger(fileSystem).use { logger ->
    SortCommand(
      logger = logger,
      fileSystem = fileSystem
    ).main(args)
  }
}

private fun logger(fileSystem: FileSystem): DelegatingLogger {
  val logDir = fileSystem.getPath(
    System.getProperty("java.io.tmpdir"),
    "dependencies-sorter"
  ).createDirectories()
  val logFile = Files.createFile(logDir.resolve("${Instant.now().toString().replace(":", "-")}.log"))

  return DelegatingLogger(
    delegate = LoggerFactory.getLogger("Sorter"),
    file = logFile
  )
}
