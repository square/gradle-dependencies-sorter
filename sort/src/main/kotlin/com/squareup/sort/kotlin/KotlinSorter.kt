package com.squareup.sort.kotlin

import cash.grammar.kotlindsl.model.gradle.DependencyContainer
import cash.grammar.kotlindsl.parse.Parser
import cash.grammar.kotlindsl.utils.Blocks.isDependencies
import cash.grammar.kotlindsl.utils.CollectingErrorListener
import cash.grammar.kotlindsl.utils.Context.fullText
import cash.grammar.kotlindsl.utils.DependencyExtractor
import cash.grammar.kotlindsl.utils.Whitespace
import com.squareup.cash.grammar.KotlinParser.NamedBlockContext
import com.squareup.cash.grammar.KotlinParser.StatementContext
import com.squareup.cash.grammar.KotlinParserBaseListener
import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.DependencyComparator
import com.squareup.sort.Ordering
import com.squareup.sort.RewrittenBlock
import com.squareup.sort.Sorter
import com.squareup.sort.Texts
import com.squareup.utils.ifNotEmpty
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
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

  /** Returns `true` if this file's dependencies are already sorted correctly, or if there are no dependencies. */
  override fun isSorted(): Boolean = ordering.isAlreadyOrdered()

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

    if (ctx.isDependencies) {
      collectDependencies(dependencyExtractor.collectDependencies(ctx))
    }
  }

  override fun exitNamedBlock(ctx: NamedBlockContext) {
    if (ctx.isDependencies) {
      val rewrittenBlock = dependenciesBlock(ctx)

      // Leave sorted blocks untouched so their existing formatting is preserved.
      if (!rewrittenBlock.isAlreadyOrdered) {
        rewriter.replace(ctx.start, ctx.stop, rewrittenBlock.text)
      }

      // Whenever we exit a dependencies block, clear this map. Each block will be treated separately.
      mutableDependencies.clear()
    }

    dependencyExtractor.onExitBlock()
  }

  private fun collectDependencies(container: DependencyContainer) {
    val declarations = container.getDependencyDeclarations().map { KotlinDependencyDeclaration(it) }
    mutableDependencies.statements += container.getStatements()

    ordering.addAll(declarations)

    declarations.forEach { decl ->
      mutableDependencies.dependenciesByConfiguration.merge(
        decl.configuration,
        mutableListOf(decl)
      ) { acc, inc ->
        acc.apply { addAll(inc) }
      }
    }
  }

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
        appendLine(stmt.fullText(input)!!)

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

      if (didWrite && mutableDependencies.declarations().isNotEmpty()) {
        appendLine()
      }

      // straightforward declarations
      mutableDependencies.declarations()
        .sortedWith(KotlinConfigurationComparator)
        .forEachIndexed { i, entry ->
          // Place a blank line between chunks of the same configuration, if configured
          if (i != 0 && config.insertBlankLines) appendLine()

          entry.value.sortedWith(dependencyComparator)
            .map { dependency ->
              dependency to Texts(
                comment = dependency.precedingComment(),
                declarationText = dependency.fullText(),
              )
            }
            .distinctBy { (_, texts) -> texts }
            .forEach { (declaration, texts) ->
              newOrder += declaration

              // Write preceding comments if there are any
              if (texts.comment != null) appendLine(texts.comment.replace("\r", ""))

              append(bodyIndent)
              appendLine(texts.declarationText.replace("\r", ""))
            }
        }

      append(blockIndent)
      append("}")
    }.replace("\n", lineSeparator)

    return RewrittenBlock(
      text = text,
      isAlreadyOrdered = ordering.checkOrdering(newOrder),
    )
  }

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

  fun declarations() = dependenciesByConfiguration.entries

  fun clear() {
    dependenciesByConfiguration.clear()
    expressions.clear()
    statements.clear()
  }
}
