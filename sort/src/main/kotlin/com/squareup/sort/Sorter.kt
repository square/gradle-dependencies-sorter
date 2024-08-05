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

  public companion object {
    public fun of(file: Path): Sorter = if (file.pathString.endsWith(".gradle")) {
      GroovySorter.of(file)
    } else if (file.pathString.endsWith(".gradle.kts")) {
      KotlinSorter.of(file)
    } else {
      error("Expected '.gradle' or '.gradle.kts' extension. Was ${file.pathString}")
    }
  }
}
