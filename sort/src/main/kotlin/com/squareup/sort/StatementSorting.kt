package com.squareup.sort

/** A movable direct call and the two keys used to order it. */
internal data class SortableStatement(
  val index: Int,
  val callName: String,
  val sourceText: String,
)

/**
 * Sorts each contiguous run by terminal call name, then rewritten source text.
 *
 * Null entries are fixed statements that split runs. Unchanged runs produce no replacement, while [replacementFor]
 * keeps syntax-specific trivia and comment attachment out of this shared ordering code.
 */
internal fun rewriteSortableRuns(
  blockStartIndex: Int,
  blockStopIndex: Int,
  sortableStatements: List<SortableStatement?>,
  sourceWithRewrites: (Int, Int, List<SourceReplacement>) -> String,
  replacementFor: (
    original: List<SortableStatement>,
    sorted: List<SortableStatement>,
  ) -> SourceReplacement,
): RewrittenBlock {
  val replacements = buildList<SourceReplacement> {
    var index = 0
    while (index < sortableStatements.size) {
      if (sortableStatements[index] == null) {
        index++
        continue
      }

      val start = index
      while (index < sortableStatements.size && sortableStatements[index] != null) index++
      val original = sortableStatements.subList(start, index).filterNotNull()
      val sorted = original.sortedWith(
        compareBy<SortableStatement> { it.callName }
          .thenBy { it.sourceText }
      )
      if (original.map { it.index } != sorted.map { it.index }) {
        add(replacementFor(original, sorted))
      }
    }
  }

  return RewrittenBlock(
    text = sourceWithRewrites(blockStartIndex, blockStopIndex, replacements),
    isAlreadyOrdered = replacements.isEmpty(),
  )
}
