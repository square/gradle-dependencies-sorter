package com.squareup.log

import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.pathString

class DelegatingLogger(
  private val delegate: Logger,
  private val file: Path,
  private val quiet: Boolean
) : AutoCloseable, Logger by delegate {

  private val start = System.currentTimeMillis()
  private var hasErrors = false

  override fun close() {
    val duration = System.currentTimeMillis() - start
    if (!quiet) {
      delegate.info("Operation took $duration ms")
    }
    if (hasErrors || !quiet) {
      delegate.info("See log file at ${file.pathString} ")
    }
  }

  override fun info(msg: String) {
    file.appendLine("INFO: $msg")
    if (!quiet) {
      delegate.info(msg)
    }
  }

  override fun trace(msg: String) {
    file.appendLine("TRACE: $msg")
    if (!quiet) {
      delegate.trace(msg)
    }
  }

  override fun warn(msg: String) {
    file.appendLine("WARN: $msg")
    if (!quiet) {
      delegate.trace(msg)
    }
  }

  override fun error(msg: String) {
    hasErrors = true
    file.appendLine("ERROR: $msg")
    delegate.error(msg)
  }

  override fun error(
    msg: String,
    t: Throwable
  ) {
    hasErrors = true
    file.appendLine("ERROR: $msg, ${t.localizedMessage}")
    delegate.error(msg, t)
  }

  private fun Path.appendLine(msg: String) {
    appendText("$msg\n")
  }
}
