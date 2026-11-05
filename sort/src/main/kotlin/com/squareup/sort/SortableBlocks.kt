package com.squareup.sort

/** Parses configured dotted paths and adds the default `dependencies.constraints` path. */
internal fun Sorter.Config.sortableBlockPaths(): List<List<String>> {
  return (setOf("dependencies.constraints") + blocks).map { path ->
    val segments = path.split('.').map(String::trim)
    require(segments.all(String::isNotEmpty)) { "Invalid block path '$path'." }
    segments
  }
}

/**
 * Returns true when the current nesting path ends with a configured path.
 *
 * An empty stack frame resets matching across anonymous closures or block-shaped text found inside a string.
 */
internal fun Collection<List<String>>.matchesSortableBlock(
  blockPathStack: Collection<List<String>>,
): Boolean {
  val frames = blockPathStack.toList()
  val currentPath = frames.drop(frames.indexOfLast { it.isEmpty() } + 1).flatten()
  return any { path ->
    currentPath.size >= path.size && currentPath.takeLast(path.size) == path
  }
}
