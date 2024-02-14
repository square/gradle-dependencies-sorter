package com.squareup.sort

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(vararg args: String) {
  println("Version=${getVersionFromResources()}")
  val cli = CommandLine(SortCommand())
  val exitCode = cli.execute(*args)
  exitProcess(exitCode)
}

fun getVersionFromResources(): String? {
  val versionKeyValue = (Any::class as Any).javaClass.getResourceAsStream("/appinfo.properties")
    ?.bufferedReader()
    ?.readLines()
    ?.first()

  return versionKeyValue?.substringAfter("=")
}
