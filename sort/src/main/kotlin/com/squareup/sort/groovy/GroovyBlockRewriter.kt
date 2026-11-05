package com.squareup.sort.groovy

import com.autonomousapps.grammar.gradle.GradleScript
import com.autonomousapps.grammar.gradle.GradleScript.BlockContext
import com.autonomousapps.grammar.gradle.GradleScript.DependenciesContext
import com.squareup.sort.RewrittenBlock
import com.squareup.sort.SourceReplacement
import com.squareup.sort.SortableStatement
import com.squareup.sort.rewriteSortableRuns
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Sorts direct calls in one Groovy DSL block.
 *
 * The grammar exposes body nodes rather than reliable statement boundaries, so this reconstructs statements from the
 * source first. Unrecognized statements stay fixed and split adjacent sortable calls into independent runs.
 */
internal class GroovyBlockRewriter(
  private val source: GroovySource,
  private val sourceWithRewrites: (Int, Int, List<SourceReplacement>) -> String,
) {

  /** Rewrites each sortable run while leaving its surrounding blank-line slots in place. */
  fun rewrite(ctx: BlockContext): RewrittenBlock {
    val bodyStartIndex = ctx.BRACE_OPEN().symbol.stopIndex + 1
    val bodyStopIndex = ctx.BRACE_CLOSE().symbol.startIndex - 1
    val statements = directStatements(ctx, bodyStartIndex, bodyStopIndex)
    val sortableStatements = statements.mapIndexed { index, statement ->
      statement.sortableCall()?.let { callName ->
        SortableStatement(
          index = index,
          callName = callName,
          sourceText = sourceWithRewrites(statement.startIndex, statement.stopIndex),
        )
      }
    }

    return rewriteSortableRuns(
      blockStartIndex = ctx.start.startIndex,
      blockStopIndex = ctx.stop.stopIndex,
      sortableStatements = sortableStatements,
      sourceWithRewrites = sourceWithRewrites,
    ) { original, sorted ->
      val replacementStart = statementEntryStart(
        original.first().index,
        bodyStartIndex,
        statements,
        bodyStopIndex,
      )
      val replacementEnd = statementEntryEnd(
        original.last().index,
        statements,
        bodyStopIndex,
      )
      val replacementText = buildString {
        // Keep blank-line slots fixed while moving calls and their attached comments.
        original.zip(sorted).forEach { (slot, sortable) ->
          val slotPayloadStart = statementPayloadStart(
            slot.index,
            bodyStartIndex,
            statements,
            bodyStopIndex,
          )
          append(
            sourceWithRewrites(
              statementEntryStart(slot.index, bodyStartIndex, statements, bodyStopIndex),
              slotPayloadStart - 1,
            )
          )

          val payloadStart = statementPayloadStart(
            sortable.index,
            bodyStartIndex,
            statements,
            bodyStopIndex,
          )
          append(
            sourceWithRewrites(
              payloadStart,
              statementEntryEnd(sortable.index, statements, bodyStopIndex),
            )
          )
        }
      }
      SourceReplacement(replacementStart, replacementEnd, replacementText)
    }
  }

  /** Groups direct body nodes at code line breaks outside balanced parentheses and brackets. */
  private fun directStatements(
    ctx: BlockContext,
    bodyStartIndex: Int,
    bodyStopIndex: Int,
  ): List<GroovyStatement> {
    val bodyNodes = ctx.children.orEmpty()
      .filterIsInstance<ParserRuleContext>()
      .filter { child ->
        child.start.startIndex >= bodyStartIndex && child.stop.stopIndex <= bodyStopIndex
      }
    if (bodyNodes.isEmpty()) return emptyList()

    return buildList {
      val statementNodes = mutableListOf<ParserRuleContext>()
      var hasSemicolon = false
      var parentheses = 0
      var brackets = 0

      bodyNodes.forEachIndexed { index, node ->
        if (index != 0) {
          val previous = bodyNodes[index - 1]
          val startsNewStatement = parentheses == 0 &&
            brackets == 0 &&
            source.hasCodeLineBreak(previous.stop.stopIndex + 1, node.start.startIndex - 1) &&
            !isLineContinuation(previous, node)
          if (startsNewStatement) {
            add(GroovyStatement(statementNodes.toList(), hasSemicolon))
            statementNodes.clear()
            hasSemicolon = false
          }
        }

        statementNodes += node
        if (node !is GradleScript.SeaContext) return@forEachIndexed
        for (position in node.start.startIndex..node.stop.stopIndex) {
          if (!source.isCode(position)) continue
          when (source.codePointAt(position)) {
            '('.code -> parentheses++
            ')'.code -> if (parentheses > 0) parentheses--
            '['.code -> brackets++
            ']'.code -> if (brackets > 0) brackets--
            ';'.code -> if (parentheses == 0 && brackets == 0) hasSemicolon = true
          }
        }
      }
      add(GroovyStatement(statementNodes, hasSemicolon))
    }
  }

  /** Keeps operator and chained-call continuations together; a closure arrow starts a new body statement. */
  private fun isLineContinuation(
    previous: ParserRuleContext,
    next: ParserRuleContext,
  ): Boolean {
    val previousText = source.text(
      maxOf(0, previous.stop.stopIndex - 1),
      previous.stop.stopIndex,
    )
    if (previousText.endsWith("->")) return false

    val previousChar = source.lastCodePoint(previous.start.startIndex, previous.stop.stopIndex)
    val nextChar = source.firstCodePoint(next.start.startIndex, next.stop.stopIndex)
    return previousChar != null && previousChar in CONTINUATION_SUFFIXES ||
      nextChar != null && nextChar in CONTINUATION_PREFIXES
  }

  /** Returns the terminal name of a direct block or call that is safe to move, or null for a fixed statement. */
  private fun GroovyStatement.sortableCall(): String? {
    if (hasSemicolon) return null

    val first = nodes.first()
    if (nodes.size == 1 && first is BlockContext && source.isCode(first.start.startIndex)) {
      val callName = first.ID().text.substringAfterLast('.')
      return callName.takeUnless { it in NON_CALL_KEYWORDS }
    }
    if (nodes.size == 1 && first is DependenciesContext && source.isCode(first.start.startIndex)) {
      return "dependencies"
    }
    if (!source.isCode(first.start.startIndex)) return null

    val firstText = source.text(first.start.startIndex, first.stop.stopIndex)
    val callPath = when {
      first.start.type == GradleScript.ID -> firstText
      firstText.endsWith("(") -> firstText.dropLast(1)
      else -> return null
    }
    val callName = callPath.substringAfterLast('.')
    if (callName in NON_CALL_KEYWORDS) return null

    val hasPrefixedParenthesis = firstText.endsWith("(")
    val firstArgument = nodes.getOrNull(1) ?: return null
    return if (hasPrefixedParenthesis ||
      source.isCode(firstArgument.start.startIndex) &&
      source.codePointAt(firstArgument.start.startIndex) == '('.code
    ) {
      callName.takeIf { isParenthesizedCall(nodes, hasPrefixedParenthesis) }
    } else {
      callName.takeIf { isCommandCall(callName, nodes.drop(1)) }
    }
  }

  /** Accepts one balanced parenthesized call followed only by an optional trailing closure. */
  private fun isParenthesizedCall(
    nodes: List<ParserRuleContext>,
    hasPrefixedParenthesis: Boolean,
  ): Boolean {
    var parentheses = if (hasPrefixedParenthesis) 1 else 0
    val start = 1
    nodes.drop(start).forEachIndexed { index, node ->
      if (node is GradleScript.ClosureContext && parentheses == 0) {
        return index == nodes.size - start - 1
      }
      for (position in node.start.startIndex..node.stop.stopIndex) {
        if (!source.isCode(position)) continue
        when (source.codePointAt(position)) {
          '('.code -> parentheses++
          ')'.code -> parentheses--
          else -> if (parentheses == 0) return false
        }
      }
      if (parentheses < 0) return false
    }
    return parentheses == 0
  }

  /** Conservatively accepts Groovy command calls while rejecting declarations, assignments, and closure parameters. */
  private fun isCommandCall(
    callName: String,
    arguments: List<ParserRuleContext>,
  ): Boolean {
    if (arguments.isEmpty()) return false
    if (callName.firstOrNull()?.isUpperCase() == true) return false
    val argumentText = source.text(
      arguments.first().start.startIndex,
      arguments.last().stop.stopIndex,
    )
    if (argumentText.contains("->")) return false
    if (arguments.any { it.start.type == GradleScript.EQUALS }) return false

    val first = arguments.first()
    if (source.isString(first.start.startIndex)) return true
    val firstText = source.text(first.start.startIndex, first.stop.stopIndex)
    if (first.start.type == GradleScript.DIGIT ||
      firstText.startsWith("[") ||
      first.start.type == GradleScript.PROJECT ||
      first.start.type == GradleScript.PROJECT_ACCESSOR
    ) {
      return true
    }
    return callName.firstOrNull()?.isLowerCase() == true ||
      firstText.any { it == '.' || it == ':' } ||
      arguments.any { argument ->
        argument.start.type == GradleScript.PARENS_OPEN ||
          argument.start.type == GradleScript.COMMA
      }
  }

  /** Returns the start of the source slot that another statement payload may move into. */
  private fun statementEntryStart(
    index: Int,
    bodyStartIndex: Int,
    statements: List<GroovyStatement>,
    bodyStopIndex: Int,
  ): Int {
    return if (index == 0) {
      bodyStartIndex
    } else {
      statementEntryEnd(index - 1, statements, bodyStopIndex) + 1
    }
  }

  /** Includes trailing inline text and multiline comments, stopping at the first line break outside a comment. */
  private fun statementEntryEnd(
    index: Int,
    statements: List<GroovyStatement>,
    bodyStopIndex: Int,
  ): Int {
    val statement = statements[index]
    val trailingStart = statement.stopIndex + 1
    val trailingLimit = if (index == statements.lastIndex) {
      bodyStopIndex
    } else {
      statements[index + 1].startIndex - 1
    }
    if (trailingStart > trailingLimit) return statement.stopIndex

    for (cursor in trailingStart..trailingLimit) {
      val char = source.codePointAt(cursor)
      if ((char == '\n'.code || char == '\r'.code) && !source.isComment(cursor)) {
        return cursor - 1
      }
    }
    return trailingLimit
  }

  /** Includes contiguous comment-only lines with the payload but leaves the slot's blank-line prefix in place. */
  private fun statementPayloadStart(
    index: Int,
    bodyStartIndex: Int,
    statements: List<GroovyStatement>,
    bodyStopIndex: Int,
  ): Int {
    val entryStart = statementEntryStart(index, bodyStartIndex, statements, bodyStopIndex)
    var payloadStart = statements[index].startIndex
    while (payloadStart > entryStart) {
      val previous = source.codePointAt(payloadStart - 1)
      if (previous == '\n'.code || previous == '\r'.code) break
      payloadStart--
    }

    while (payloadStart > entryStart) {
      var previousLineEnd = payloadStart - 1
      if (source.codePointAt(previousLineEnd) == '\n'.code) previousLineEnd--
      if (previousLineEnd >= entryStart && source.codePointAt(previousLineEnd) == '\r'.code) {
        previousLineEnd--
      }
      if (previousLineEnd < entryStart) break

      var previousLineStart = previousLineEnd
      while (previousLineStart > entryStart) {
        val previous = source.codePointAt(previousLineStart - 1)
        if (previous == '\n'.code || previous == '\r'.code) break
        previousLineStart--
      }
      val line = previousLineStart..previousLineEnd
      val hasComment = line.any(source::isComment)
      val isCommentOnly = line.all { position ->
        source.isComment(position) ||
          source.isCode(position) && Character.isWhitespace(source.codePointAt(position))
      }
      if (!hasComment || !isCommentOnly) break
      payloadStart = previousLineStart
    }
    return payloadStart
  }

  private fun sourceWithRewrites(startIndex: Int, stopIndex: Int): String {
    return sourceWithRewrites(startIndex, stopIndex, emptyList())
  }

  private companion object {
    val NON_CALL_KEYWORDS = setOf(
      "abstract",
      "assert",
      "boolean",
      "break",
      "byte",
      "case",
      "catch",
      "char",
      "class",
      "continue",
      "def",
      "do",
      "double",
      "else",
      "enum",
      "final",
      "finally",
      "float",
      "for",
      "if",
      "import",
      "in",
      "int",
      "interface",
      "long",
      "native",
      "new",
      "package",
      "private",
      "protected",
      "public",
      "return",
      "short",
      "static",
      "strictfp",
      "switch",
      "synchronized",
      "throw",
      "trait",
      "transient",
      "try",
      "void",
      "volatile",
      "while",
    )
    val CONTINUATION_PREFIXES = setOf(
      '.'.code,
      '?'.code,
      '*'.code,
      '+'.code,
      '-'.code,
      '/'.code,
      '%'.code,
      '&'.code,
      '|'.code,
      '^'.code,
    )
    val CONTINUATION_SUFFIXES = CONTINUATION_PREFIXES + setOf(
      '='.code,
      '<'.code,
      '>'.code,
      ','.code,
      '\\'.code,
    )
  }
}

/** Parser nodes reconstructed into one direct source statement. */
private data class GroovyStatement(
  val nodes: List<ParserRuleContext>,
  val hasSemicolon: Boolean,
) {
  val startIndex: Int
    get() = nodes.first().start.startIndex
  val stopIndex: Int
    get() = nodes.last().stop.stopIndex
}
