package com.squareup.sort

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

class BuildDotGradleFinder(
  private val root: Path,
  searchPaths: List<String>,
  skipHiddenAndBuildDirs: Boolean,
) {

  private val traversalFilter: (File) -> Boolean = if (skipHiddenAndBuildDirs) {
    { dir -> !dir.name.startsWith(".") && dir.name != "build" }
  } else {
    { true }
  }

  val buildDotGradles: Set<Path> = searchPaths.asSequence()
    // nb, if the path passed to the resolve method is already an absolute path, it returns that.
    .map { root.resolve(it).normalize() }
    .flatMap { searchPath ->
      if (searchPath.isBuildDotGradle()) {
        sequenceOf(searchPath).filter(Files::exists)
      } else {
        // Use File.walk() so we have access to the `onEnter` filter.
        // Can switch to Path.walk() once it's stable and offers the same API.
        searchPath.toFile().walk()
          .onEnter(traversalFilter)
          .map(File::toPath)
          .filter(Path::isBuildDotGradle)
          .filter(Files::exists)
      }
    }
    .toSet()

  interface Factory {
    fun of(
      root: Path,
      searchPaths: List<String>,
      skipHiddenAndBuildDirs: Boolean,
    ): BuildDotGradleFinder = BuildDotGradleFinder(root, searchPaths, skipHiddenAndBuildDirs)
  }
}

private fun Path.isBuildDotGradle(): Boolean {
  val filename = fileName.pathString
  return filename == "build.gradle" || filename == "build.gradle.kts"
}
