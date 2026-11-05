package com.squareup.sort

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.TokenStreamRewriter

/** Rendered text for one block and whether its statements were already ordered. */
internal data class RewrittenBlock(
  val text: String,
  val isAlreadyOrdered: Boolean,
)

/** Replaces the inclusive source range from [startIndex] through [stopIndex] with [text]. */
internal data class SourceReplacement(
  val startIndex: Int,
  val stopIndex: Int,
  val text: String,
)

/** Stable source and token ranges for a parsed block. */
private data class BlockRange(
  val sourceStartIndex: Int,
  val sourceStopIndex: Int,
  val tokenStartIndex: Int,
  val tokenStopIndex: Int,
) {
  /** Returns true when [other] is wholly contained by this source range. */
  fun contains(other: BlockRange): Boolean {
    return other.isWithin(sourceStartIndex, sourceStopIndex)
  }

  /** Returns true when this block is wholly contained by the inclusive source range. */
  fun isWithin(startIndex: Int, stopIndex: Int): Boolean {
    return sourceStartIndex >= startIndex && sourceStopIndex <= stopIndex
  }
}

/**
 * Stores block rewrites, composes changed descendants when rendering, and applies only outermost changes.
 *
 * Parse-tree listeners visit children first. A parent rewrite therefore renders its changed children before the final
 * token rewrite is scheduled.
 */
internal class RewrittenBlocks(
  private val originalText: (Int, Int) -> String,
) {
  private val blocks = linkedMapOf<BlockRange, RewrittenBlock>()

  val isAlreadyOrdered: Boolean
    get() = blocks.values.all { it.isAlreadyOrdered }

  /** Records the rendered result for a block after its descendants have been visited. */
  operator fun set(block: ParserRuleContext, rewritten: RewrittenBlock) {
    blocks[block.toBlockRange()] = rewritten
  }

  /** Renders [block] with any changed descendant blocks already applied. */
  fun render(block: ParserRuleContext): String {
    return render(block.start.startIndex, block.stop.stopIndex)
  }

  /** Renders an inclusive source range with any changed descendant blocks already applied. */
  fun render(startIndex: Int, stopIndex: Int): String {
    return render(startIndex, stopIndex, emptyList())
  }

  /**
   * Renders an inclusive source range with [replacements] and its outermost changed descendants.
   *
   * Descendants already covered by an explicit replacement are skipped because that replacement rendered them.
   */
  fun render(
    startIndex: Int,
    stopIndex: Int,
    replacements: List<SourceReplacement>,
  ): String {
    val descendants = blocks.entries
      .filter { (block, rewritten) ->
        !rewritten.isAlreadyOrdered &&
          block.isWithin(startIndex, stopIndex) &&
          replacements.none { replacement ->
            block.isWithin(replacement.startIndex, replacement.stopIndex)
          }
      }
      .filter { candidate ->
        blocks.entries.none { other ->
          !other.value.isAlreadyOrdered &&
            other.key != candidate.key &&
            other.key.isWithin(startIndex, stopIndex) &&
            other.key.contains(candidate.key)
        }
      }
      .map { (block, rewritten) ->
        SourceReplacement(block.sourceStartIndex, block.sourceStopIndex, rewritten.text)
      }

    val allReplacements = (replacements + descendants).sortedBy { it.startIndex }

    return buildString {
      var cursor = startIndex
      allReplacements.forEach { replacement ->
        if (cursor < replacement.startIndex) {
          append(originalText(cursor, replacement.startIndex - 1))
        }
        append(replacement.text)
        cursor = replacement.stopIndex + 1
      }
      if (cursor <= stopIndex) {
        append(originalText(cursor, stopIndex))
      }
    }
  }

  /** Applies only outermost changes because their rendered text already contains changed descendants. */
  fun applyTo(rewriter: TokenStreamRewriter) {
    val changed = blocks.entries.filter { !it.value.isAlreadyOrdered }
    changed.filter { candidate ->
      changed.none { other ->
        other.key != candidate.key && other.key.contains(candidate.key)
      }
    }.forEach { (block, rewritten) ->
      rewriter.replace(block.tokenStartIndex, block.tokenStopIndex, rewritten.text)
    }
  }
}

/** Captures immutable source and token positions instead of retaining the parser context. */
private fun ParserRuleContext.toBlockRange(): BlockRange {
  return BlockRange(
    sourceStartIndex = start.startIndex,
    sourceStopIndex = stop.stopIndex,
    tokenStartIndex = start.tokenIndex,
    tokenStopIndex = stop.tokenIndex,
  )
}
