package com.squareup.sort

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.pathString

class BuildDotGradleFinder(
  searchPaths: List<Path>
) {

  val buildDotGradles: Set<Path> = searchPaths.asSequence()
    .flatMap { searchPath ->
      Files.walk(searchPath).use { paths ->
        paths.parallel()
          .filter(Path::isBuildDotGradle)
          .collect(Collectors.toUnmodifiableList())
      }
    }
    .toSet()

  interface Factory {
    fun of(searchPaths: List<Path>): BuildDotGradleFinder = BuildDotGradleFinder(searchPaths)

    object Default : Factory {
      override fun of(searchPaths: List<Path>): BuildDotGradleFinder {
        return BuildDotGradleFinder(searchPaths)
      }
    }
  }
}

private fun Path.isBuildDotGradle(): Boolean {
  val filename = fileName.pathString
  return filename == "build.gradle" || filename == "build.gradle.kts"
}
