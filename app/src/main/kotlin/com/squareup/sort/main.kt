package com.squareup.sort

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(vararg args: String) {
  val cli = CommandLine(SortCommand())
  val exitCode = cli.execute(*args)
  exitProcess(exitCode)
}
