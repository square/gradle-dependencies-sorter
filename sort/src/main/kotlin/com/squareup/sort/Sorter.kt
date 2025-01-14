package com.squareup.sort

import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.groovy.GroovySorter
import com.squareup.sort.kotlin.KotlinSorter
import java.nio.file.Path
import kotlin.io.path.pathString

public interface Sorter {
  public fun rewritten(): String
  public fun isSorted(): Boolean
  public fun hasParseErrors(): Boolean
  public fun getParseError(): BuildScriptParseException?

  public data class Config(
    public val insertBlankLines: Boolean,
  )

  public companion object {
    public fun defaultConfig(): Config = Config(
      insertBlankLines = true,
    )

    @JvmOverloads
    public fun of(
      file: Path,
      config: Config = defaultConfig(),
    ): Sorter = if (file.pathString.endsWith(".gradle")) {
      GroovySorter.of(file, config)
    } else if (file.pathString.endsWith(".gradle.kts")) {
      KotlinSorter.of(file, config)
    } else {
      error("Expected '.gradle' or '.gradle.kts' extension. Was ${file.pathString}")
    }
  }
}
