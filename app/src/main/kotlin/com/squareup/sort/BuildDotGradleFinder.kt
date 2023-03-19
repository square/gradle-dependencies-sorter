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
      it.fastWalk()
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

/**
 * Gets a sequence for visiting this directory and all its content but skips `build` directories
 * and directories that starts with a `.` like `.git`, `.gradle`, etc. for better performances
 * as they generally don't contain Gradle build files, but a lot of uninteresting nested files and directories.
 */
private fun File.fastWalk(): Sequence<File> = walk().onEnter {
  it.isDirectory && !it.name.startsWith(".") && it.name != "build"
}

private fun File.isBuildDotGradle(): Boolean {
  return isFile && name == "build.gradle" || name == "build.gradle.kts"
}
