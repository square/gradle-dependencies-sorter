package com.squareup.sort

import java.io.File
import java.nio.file.Path

class BuildDotGradleFinder(
  private val root: Path,
  searchPaths: List<String>
) {

  val buildDotGradles: Set<Path> = searchPaths.asSequence()
    // nb, if the path passed to the resolve method is already an absolute path, it returns that.
    .map { root.resolve(it).toFile() }
    .flatMap {
      it.walk()
        .filter(File::isBuildDotGradle)
        .map(File::toPath)
    }
    .toSet()

  interface Factory {
    fun of(
      root: Path,
      searchPaths: List<String>
    ): BuildDotGradleFinder = BuildDotGradleFinder(root, searchPaths)
  }
}

private fun File.isBuildDotGradle(): Boolean {
  return isFile && name == "build.gradle" || name == "build.gradle.kts"
}
