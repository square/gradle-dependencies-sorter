package com.squareup.sort

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.pathString

class BuildDotGradleFinder(
  private val root: Path,
  searchPaths: List<String>
) {

  val buildDotGradles: Set<Path> = searchPaths.asSequence()
    // nb, if the path passed to the resolve method is already an absolute path, it returns that.
    .map { root.resolve(it) }
    .flatMap { searchPath ->
      Files.walk(searchPath).use { paths ->
        paths.parallel()
          .filter(Path::isBuildDotGradle)
          .collect(Collectors.toUnmodifiableList())
      }
    }
    .toSet()

  interface Factory {
    fun of(
      root: Path,
      searchPaths: List<String>
    ): BuildDotGradleFinder = BuildDotGradleFinder(root, searchPaths)

    object Default : Factory {
      override fun of(root: Path, searchPaths: List<String>): BuildDotGradleFinder {
        return BuildDotGradleFinder(root, searchPaths)
      }
    }
  }
}

private fun Path.isBuildDotGradle(): Boolean {
  val filename = fileName.pathString
  return filename == "build.gradle" || filename == "build.gradle.kts"
}
