package com.squareup.sort

import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.FileVisitorBuilder
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.visitFileTree

private val BUILD_FILE_REGEX: Regex = "^.*\\.gradle(\\.kts)?$".toRegex()

@OptIn(ExperimentalPathApi::class)
class BuildDotGradleFinder(
  private val root: Path,
  searchPaths: List<Path>,
  skipHiddenAndBuildDirs: Boolean,
) {

  /**
   * Skips `build` and cache directories (starting with `.`, like `.gradle`) in [walkEachFile].
   */
  private fun FileVisitorBuilder.skipBuildAndCacheDirs() {
    return onPreVisitDirectory { dir, _ ->
      if (dir.name.startsWith(".") || dir.name == "build") {
        FileVisitResult.SKIP_SUBTREE
      } else {
        FileVisitResult.CONTINUE
      }
    }
  }

  private fun Path.walkEachFile(
    maxDepth: Int = Int.MAX_VALUE,
    followLinks: Boolean = false,
    builderAction: FileVisitorBuilder.() -> Unit = {},
  ): Sequence<Path> {
    val files = mutableListOf<Path>()
    visitFileTree(maxDepth, followLinks) {
      builderAction()
      onVisitFile { file, _ ->
        files.add(file)
        FileVisitResult.CONTINUE
      }
    }
    return files.asSequence()
  }

  val buildDotGradles: Set<Path> = searchPaths.asSequence()
    // nb, if the path passed to the resolve method is already an absolute path, it returns that.
    .map { root.resolve(it).normalize() }
    .flatMap { searchPath ->
      if (searchPath.isModuleBuildFile()) {
        sequenceOf(searchPath).filter(Path::exists)
      } else {
        // Use File.walk() so we have access to the `onEnter` filter.
        // Can switch to Path.walk() once it's stable and offers the same API.
        searchPath.walkEachFile {
          if (skipHiddenAndBuildDirs) {
            skipBuildAndCacheDirs()
          }
        }
          .filter(Path::isModuleBuildFile)
          .filter(Path::exists)
      }
    }
    .toSet()

  interface Factory {
    fun of(
      root: Path,
      searchPaths: List<Path>,
      skipHiddenAndBuildDirs: Boolean,
    ): BuildDotGradleFinder = BuildDotGradleFinder(root, searchPaths, skipHiddenAndBuildDirs)

    object Default : Factory {
      override fun of(root: Path, searchPaths: List<Path>, skipHiddenAndBuildDirs: Boolean): BuildDotGradleFinder {
        return BuildDotGradleFinder(root, searchPaths, skipHiddenAndBuildDirs)
      }
    }
  }
}

private fun Path.isModuleBuildFile(): Boolean {
  val filename = fileName.pathString
  return filename.matches(BUILD_FILE_REGEX)
}
