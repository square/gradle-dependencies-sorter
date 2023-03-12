package com.squareup.sort

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.pathString
import kotlin.io.path.walk

class BuildDotGradleFinder(
  private val root: Path,
  searchPaths: List<String>
) {

  @OptIn(ExperimentalPathApi::class)
  val buildDotGradles: Set<Path> = searchPaths.asSequence()
    // nb, if the path passed to the resolve method is already an absolute path, it returns that.
    .map { root.resolve(it) }
    .flatMap { searchPath ->
      if (searchPath.isBuildDotGradle()) {
        sequenceOf(searchPath)
      } else {
        searchPath.walk().filter(Path::isBuildDotGradle)
      }
    }
    .toSet()

  interface Factory {
    fun of(
      root: Path,
      searchPaths: List<String>
    ): BuildDotGradleFinder = BuildDotGradleFinder(root, searchPaths)
  }
}

private fun Path.isBuildDotGradle(): Boolean {
  val filename = fileName.pathString
  return filename == "build.gradle" || filename == "build.gradle.kts"
}
