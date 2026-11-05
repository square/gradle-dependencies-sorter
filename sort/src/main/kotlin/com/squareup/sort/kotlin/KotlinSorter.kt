package com.squareup.sort.kotlin

import cash.grammar.kotlindsl.model.gradle.DependencyContainer
import cash.grammar.kotlindsl.parse.Parser
import cash.grammar.kotlindsl.utils.Blocks.isDependencies
import cash.grammar.kotlindsl.utils.CollectingErrorListener
import cash.grammar.kotlindsl.utils.Context.leafRule
import cash.grammar.kotlindsl.utils.DependencyExtractor
import cash.grammar.kotlindsl.utils.Whitespace
import com.squareup.cash.grammar.KotlinParser.LambdaLiteralContext
import com.squareup.cash.grammar.KotlinParser.NamedBlockContext
import com.squareup.cash.grammar.KotlinParser.PostfixUnaryExpressionContext
import com.squareup.cash.grammar.KotlinParser.ScriptContext
import com.squareup.cash.grammar.KotlinParser.StatementContext
import com.squareup.cash.grammar.KotlinParserBaseListener
import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.DependencyComparator
import com.squareup.sort.Ordering
import com.squareup.sort.RewrittenBlock
import com.squareup.sort.RewrittenBlocks
import com.squareup.sort.SourceReplacement
import com.squareup.sort.SortableStatement
import com.squareup.sort.Sorter
import com.squareup.sort.Texts
import com.squareup.sort.appendSortedDependencies
import com.squareup.sort.matchesSortableBlock
import com.squareup.sort.rewriteSortableRuns
import com.squareup.sort.sortableBlockPaths
import com.squareup.utils.ifNotEmpty
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenStreamRewriter
import org.antlr.v4.runtime.misc.Interval
import java.nio.file.Path

public class KotlinSorter private constructor(
  private val input: CharStream,
  private val tokens: CommonTokenStream,
  private val errorListener: CollectingErrorListener,
  private val config: Sorter.Config,
  private val lineSeparator: String,
) : Sorter, KotlinParserBaseListener() {

  private val rewriter = TokenStreamRewriter(tokens)

  private val indent = Whitespace.computeIndent(tokens, input)
  private val dependencyExtractor = DependencyExtractor(
    input = input,
    tokens = tokens,
    indent = indent,
  )

  private val dependencyComparator = DependencyComparator()
  private val mutableDependencies = MutableDependencies()
  private val ordering = Ordering<KotlinDependencyDeclaration>()
  // Empty frames reset path matching across anonymous lambdas that do not belong to a direct call.
  private val blockPathStack = ArrayDeque<List<String>>()
  private val rewrittenBlocks = RewrittenBlocks { start, stop ->
    input.getText(Interval.of(start, stop))
  }
  private val sortableBlockPaths = config.sortableBlockPaths()

  /**
   * Returns the sorted build script.
   *
   * Throws [BuildScriptParseException] if the script has some idiosyncrasy that impairs parsing.
   *
   * Throws [AlreadyOrderedException] if the script is already sorted correctly.
   */
  @Throws(BuildScriptParseException::class, AlreadyOrderedException::class)
  override fun rewritten(): String {
    errorListener.getErrorMessages().ifNotEmpty {
      throw BuildScriptParseException.withErrors(it)
    }
    if (isSorted()) throw AlreadyOrderedException()

    return rewriter.text
  }

  /** Returns `true` if this file's sortable blocks are already ordered, or if there are none. */
  override fun isSorted(): Boolean = rewrittenBlocks.isAlreadyOrdered

  /** Returns `true` if there were errors parsing the build script. */
  override fun hasParseErrors(): Boolean = errorListener.getErrorMessages().isNotEmpty()

  /** Returns the parse exception if there is one, otherwise null. */
  override fun getParseError(): BuildScriptParseException? {
    return if (errorListener.getErrorMessages().isNotEmpty()) {
      BuildScriptParseException.withErrors(errorListener.getErrorMessages())
    } else {
      null
    }
  }

  override fun enterNamedBlock(ctx: NamedBlockContext) {
    dependencyExtractor.onEnterBlock()
    blockPathStack.addLast(ctx.name().Identifier().map { it.text })

    if (ctx.isDependencies) {
      collectDependencies(dependencyExtractor.collectDependencies(ctx))
    }
  }

  override fun exitNamedBlock(ctx: NamedBlockContext) {
    if (ctx.isDependencies) {
      val rewrittenBlock = dependenciesBlock(ctx)
      rewrittenBlocks[ctx] = rewrittenBlock

      // Whenever we exit a dependencies block, clear this map. Each block will be treated separately.
      mutableDependencies.clear()
    } else if (matchesSortableBlock()) {
      collectStatementBlock(
        ctx = ctx,
        bodyStartIndex = ctx.LCURL().symbol.stopIndex + 1,
        statements = ctx.statements().statement(),
      )
    }

    blockPathStack.removeLast()
    dependencyExtractor.onExitBlock()
  }

  override fun enterLambdaLiteral(ctx: LambdaLiteralContext) {
    blockPathStack.addLast(callPath(ctx))
  }

  override fun exitLambdaLiteral(ctx: LambdaLiteralContext) {
    if (matchesSortableBlock()) {
      collectStatementBlock(
        ctx = ctx,
        bodyStartIndex = ctx.ARROW()?.symbol?.stopIndex?.plus(1)
          ?: (ctx.LCURL().symbol.stopIndex + 1),
        statements = ctx.statements().statement(),
      )
    }

    blockPathStack.removeLast()
  }

  override fun exitScript(ctx: ScriptContext) {
    rewrittenBlocks.applyTo(rewriter)
  }

  private fun collectDependencies(container: DependencyContainer) {
    val declarations = container.getDependencyDeclarationsWithContext().map {
      KotlinDependencyDeclaration(it.declaration, it.statement)
    }
    mutableDependencies.statements += container.getStatements()

    ordering.addAll(declarations)

    declarations.forEach { decl ->
      mutableDependencies.dependenciesByConfiguration
        .getOrPut(decl.configuration) { mutableListOf() }
        .add(decl)
    }
  }

  /** Builds a dependency block with fixed statements first and sorted, deduplicated declarations after them. */
  private fun dependenciesBlock(ctx: NamedBlockContext): RewrittenBlock {
    val newOrder = mutableListOf<KotlinDependencyDeclaration>()

    // Blocks can be nested inside any DSL, so derive indentation from this block's source text.
    val blockIndent = indentationBefore(ctx.start).orEmpty()
    val bodyIndent = ctx.statements().statement().firstOrNull()
      ?.let { indentationBefore(it.start) }
      ?: "$blockIndent$indent"
    val text = buildString {
      var didWrite = false

      appendLine("${ctx.name().text} {")

      /*
       * not-easily-modelable elements
       */

      // An example of a statement, in this context, is an if-expression or property expression (declaration)
      mutableDependencies.statements.forEach { stmt ->
        append(bodyIndent)
        appendLine(rewrittenBlocks.render(stmt).replace("\r", ""))

        didWrite = true
      }

      if (didWrite && mutableDependencies.expressions.isNotEmpty()) {
        appendLine()
      }

      // An example of an expression, in this context, is a function call like `add("extraImplementation", "foo")`
      mutableDependencies.expressions.forEach { expr ->
        append(bodyIndent)
        appendLine(expr)

        didWrite = true
      }

      if (didWrite && mutableDependencies.dependenciesByConfiguration.isNotEmpty()) {
        appendLine()
      }

      // straightforward declarations
      newOrder += appendSortedDependencies(
        dependenciesByConfiguration = mutableDependencies.dependenciesByConfiguration,
        dependencyComparator = dependencyComparator,
        bodyIndent = bodyIndent,
        insertBlankLines = config.insertBlankLines,
      ) { dependency ->
        Texts(
          comment = dependency.precedingComment(),
          declarationText = dependency.statement
            ?.let(rewrittenBlocks::render)
            ?: dependency.fullText(),
        )
      }

      append(blockIndent)
      append("}")
    }.replace("\n", lineSeparator)

    return RewrittenBlock(
      text = text,
      isAlreadyOrdered = ordering.checkOrdering(newOrder),
    )
  }

  /**
   * Records a rewrite for [ctx] that sorts direct call statements within each uninterrupted run.
   *
   * Non-call and same-line statements stay in place and split the block into independent runs.
   * Rewritten child blocks are included when comparing and moving statements.
   */
  private fun collectStatementBlock(
    ctx: ParserRuleContext,
    bodyStartIndex: Int,
    statements: List<StatementContext>,
  ) {
    val sortableStatements = statements.mapIndexed { index, statement ->
      statement.sortableCall()?.let { callName ->
        SortableStatement(
          index = index,
          callName = callName,
          sourceText = rewrittenBlocks.render(statement),
        )
      }
    }.toMutableList()
    for (index in 1 until statements.size) {
      if (!hasLineBreakBetween(statements[index - 1], statements[index])) {
        // Leave semicolon-separated and same-line statements in place.
        sortableStatements[index - 1] = null
        sortableStatements[index] = null
      }
    }
    rewrittenBlocks[ctx] = rewriteSortableRuns(
      blockStartIndex = ctx.start.startIndex,
      blockStopIndex = ctx.stop.stopIndex,
      sortableStatements = sortableStatements,
      sourceWithRewrites = { start, stop, replacements ->
        rewrittenBlocks.render(start, stop, replacements)
      },
    ) { original, sorted ->
      val replacementStart = statementEntryStart(
        original.first().index,
        bodyStartIndex,
        statements,
        ctx,
      )
      val replacementEnd = statementEntryEnd(original.last().index, statements, ctx)
      val replacementText = buildString {
        sorted.forEach { sortable ->
          val entryStart = statementEntryStart(sortable.index, bodyStartIndex, statements, ctx)
          val entryEnd = statementEntryEnd(sortable.index, statements, ctx)
          append(rewrittenBlocks.render(entryStart, entryEnd))
        }
      }
      SourceReplacement(replacementStart, replacementEnd, replacementText)
    }
  }

  /** Returns the start of the source entry moved with a statement, including text since the previous entry. */
  private fun statementEntryStart(
    index: Int,
    bodyStartIndex: Int,
    statements: List<StatementContext>,
    ctx: ParserRuleContext,
  ): Int {
    return if (index == 0) {
      bodyStartIndex
    } else {
      statementEntryEnd(index - 1, statements, ctx) + 1
    }
  }

  /** Extends a statement through same-line trailing text but stops before the next separating line break. */
  private fun statementEntryEnd(
    index: Int,
    statements: List<StatementContext>,
    ctx: ParserRuleContext,
  ): Int {
    val statement = statements[index]
    val trailingStart = statement.stop.stopIndex + 1
    val trailingLimit = if (index == statements.lastIndex) {
      ctx.stop.startIndex - 1
    } else {
      statements[index + 1].start.startIndex - 1
    }
    if (trailingStart > trailingLimit) return statement.stop.stopIndex

    val trailing = input.getText(Interval.of(trailingStart, trailingLimit))
    val lineBreak = trailing.indexOfFirst { it == '\n' || it == '\r' }
    return if (lineBreak == -1) trailingLimit else trailingStart + lineBreak - 1
  }

  /** Same-line or semicolon-separated statements are fixed because they do not have independent source slots. */
  private fun hasLineBreakBetween(first: StatementContext, second: StatementContext): Boolean {
    val start = first.stop.stopIndex + 1
    val stop = second.start.startIndex - 1
    if (start > stop) return false
    return input.getText(Interval.of(start, stop)).any { it == '\n' || it == '\r' }
  }

  /** Returns the terminal name of a direct call or named block, or null when this statement must stay fixed. */
  private fun StatementContext.sortableCall(): String? {
    namedBlock()?.let { return it.name().Identifier().last().text }

    val expression = leafRule() as? PostfixUnaryExpressionContext ?: return null
    return expression.directCallPath().lastOrNull()
  }

  /** Finds the direct call owning [ctx], or returns an empty path for anonymous or nested lambdas. */
  private fun callPath(ctx: LambdaLiteralContext): List<String> {
    var parent = ctx.parent as? ParserRuleContext
    while (parent != null) {
      when (parent) {
        is PostfixUnaryExpressionContext -> return parent.blockCallPath(ctx)
        is StatementContext -> return emptyList()
      }
      parent = parent.parent as? ParserRuleContext
    }
    return emptyList()
  }

  /** Returns this expression's path only when it is the whole statement and [ctx] is its final trailing lambda. */
  private fun PostfixUnaryExpressionContext.blockCallPath(ctx: LambdaLiteralContext): List<String> {
    val statement = generateSequence(parent as? ParserRuleContext) { it.parent as? ParserRuleContext }
      .filterIsInstance<StatementContext>()
      .firstOrNull()
    if (statement?.leafRule() !== this) return emptyList()
    if (postfixUnarySuffix().lastOrNull()?.callSuffix()?.annotatedLambda()?.lambdaLiteral() !== ctx) {
      return emptyList()
    }
    return directCallPath()
  }

  /** Returns a dotted direct-call path, rejecting chained calls, indexing, and postfix operators. */
  private fun PostfixUnaryExpressionContext.directCallPath(): List<String> {
    val suffixes = postfixUnarySuffix()
    if (suffixes.lastOrNull()?.callSuffix() == null) return emptyList()
    if (suffixes.dropLast(1).any {
        it.callSuffix() != null || it.indexingSuffix() != null || it.postfixUnaryOperator() != null
      }
    ) {
      return emptyList()
    }

    val first = primaryExpression().simpleIdentifier()?.text ?: return emptyList()
    return buildList {
      add(first)
      suffixes.mapNotNullTo(this) {
        it.navigationSuffix()?.simpleIdentifier()?.text
      }
    }
  }

  private fun matchesSortableBlock(): Boolean {
    return sortableBlockPaths.matchesSortableBlock(blockPathStack)
  }

  /** Returns whitespace between the previous line break and [token], or null if other text intervenes. */
  private fun indentationBefore(token: Token): String? {
    if (token.startIndex <= 0) return ""

    return input.getText(Interval.of(0, token.startIndex - 1))
      .substringAfterLast('\n')
      .substringAfterLast('\r')
      .takeIf { it.all { char -> char == ' ' || char == '\t' } }
  }

  public companion object {
    @JvmStatic
    @JvmOverloads
    public fun of(file: Path, config: Sorter.Config = Sorter.defaultConfig(), lineSeparator: String = System.lineSeparator()): KotlinSorter {
      val errorListener = CollectingErrorListener()

      return Parser(
        file = Parser.readOnlyInputStream(file),
        errorListener = errorListener,
        startRule = { it.script() },
        listenerFactory = { input, tokens, _ ->
          KotlinSorter(
            input = input,
            tokens = tokens,
            errorListener = errorListener,
            config = config,
            lineSeparator = lineSeparator,
          )
        }
      ).listener()
    }
  }
}

private class MutableDependencies(
  val dependenciesByConfiguration: MutableMap<String, MutableList<KotlinDependencyDeclaration>> = mutableMapOf(),
  val expressions: MutableList<String> = mutableListOf(),
  val statements: MutableList<StatementContext> = mutableListOf(),
) {

  fun clear() {
    dependenciesByConfiguration.clear()
    expressions.clear()
    statements.clear()
  }
}
