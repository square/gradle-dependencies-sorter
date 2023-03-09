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
  val cli = CommandLine(SortCommand())
  val exitCode = cli.execute(*args)
  exitProcess(exitCode)
}
