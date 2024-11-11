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
import com.squareup.sort.Sorter
import com.squareup.sort.Texts
import com.squareup.utils.ifNotEmpty
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStreamRewriter
import java.nio.file.Path

public class KotlinSorter private constructor(
  private val input: CharStream,
  private val tokens: CommonTokenStream,
  private val errorListener: CollectingErrorListener,
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
  private val ordering = Ordering()

  private var level = 0

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
    level++

    if (ctx.isDependencies) {
      collectDependencies(dependencyExtractor.collectDependencies(ctx))
    }
  }

  override fun exitNamedBlock(ctx: NamedBlockContext) {
    if (ctx.isDependencies) {
      rewriter.replace(ctx.start, ctx.stop, dependenciesBlock())

      // Whenever we exit a dependencies block, clear this map. Each block will be treated separately.
      mutableDependencies.clear()
    }

    dependencyExtractor.onExitBlock()
    level--
  }

  private fun collectDependencies(container: DependencyContainer) {
    val declarations = container.getDependencyDeclarations().map { KotlinDependencyDeclaration(it) }
    mutableDependencies.statements += container.getStatements()

    ordering.collectDependencies(declarations)

    declarations.forEach { decl ->
      mutableDependencies.dependenciesByConfiguration.merge(
        decl.configuration,
        mutableListOf(decl)
      ) { acc, inc ->
        acc.apply { addAll(inc) }
      }
    }
  }

  private fun dependenciesBlock() = buildString {
    val newOrder = mutableListOf<KotlinDependencyDeclaration>()
    var didWrite = false

    appendLine("dependencies {")

    /*
     * not-easily-modelable elements
     */

    // An example of a statement, in this context, is an if-expression or property expression (declaration)
    mutableDependencies.statements.forEach { stmt ->
      append(indent.repeat(level))
      appendLine(stmt.fullText(input)!!)

      didWrite = true
    }

    if (didWrite && mutableDependencies.expressions.isNotEmpty()) {
      appendLine()
    }

    // An example of an expression, in this context, is a function call like `add("extraImplementation", "foo")`
    mutableDependencies.expressions.forEach { expr ->
      append(indent.repeat(level))
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
        if (i != 0) appendLine()

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
            if (texts.comment != null) appendLine(texts.comment)

            append(indent.repeat(level))
            appendLine(texts.declarationText)
          }
      }

    append(indent.repeat(level - 1))
    append("}")

    // If the new ordering matches the old ordering, we shouldn't rewrite the file. This accounts for multiple
    // dependencies blocks
    ordering.checkOrdering(newOrder)
  }

  public companion object {
    @JvmStatic
    public fun of(file: Path): KotlinSorter {
      val errorListener = CollectingErrorListener()

      return Parser(
        file = Parser.readOnlyInputStream(file),
        errorListener = errorListener,
        startRule = { it.script() },
        listenerFactory = { input, tokens, parser ->
          KotlinSorter(
            input = input,
            tokens = tokens,
            errorListener = errorListener,
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

private class Ordering {

  private val dependenciesInOrder = mutableListOf<KotlinDependencyDeclaration>()
  private val orderedBlocks = mutableListOf<Boolean>()

  fun isAlreadyOrdered(): Boolean = orderedBlocks.all { it }

  fun collectDependencies(dependencies: List<KotlinDependencyDeclaration>) {
    dependenciesInOrder += dependencies
  }

  /**
   * Checks ordering as we leave a dependencies block. Clears the list of dependencies to prepare for the potential next
   * block.
   */
  fun checkOrdering(newOrder: List<KotlinDependencyDeclaration>) {
    orderedBlocks += isSameOrder(dependenciesInOrder, newOrder)
    dependenciesInOrder.clear()
  }

  private fun isSameOrder(
    first: List<KotlinDependencyDeclaration>,
    second: List<KotlinDependencyDeclaration>,
  ): Boolean {
    if (first.size != second.size) return false
    return first.zip(second).all { (l, r) ->
      l == r
    }
  }
}
