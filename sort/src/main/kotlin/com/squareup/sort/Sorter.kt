package com.squareup.sort

import com.squareup.grammar.GradleGroovyScript
import com.squareup.grammar.GradleGroovyScript.DependenciesContext
import com.squareup.grammar.GradleGroovyScript.NormalDeclarationContext
import com.squareup.grammar.GradleGroovyScript.PlatformDeclarationContext
import com.squareup.grammar.GradleGroovyScript.TestFixturesDeclarationContext
import com.squareup.grammar.GradleGroovyScriptBaseListener
import com.squareup.grammar.GradleGroovyScriptLexer
import com.squareup.parse.AbstractErrorListener
import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.TokenStreamRewriter
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.absolutePathString

public class Sorter private constructor(
  private val tokens: CommonTokenStream,
  private val rewriter: TokenStreamRewriter,
  private val errorListener: RewriterErrorListener,
  private val filePath: String,
) : GradleGroovyScriptBaseListener() {

  // We use a default of two spaces, but update it at most once later on.
  private var smartIndentSet = false
  private var indent = "  "

  private var isInBuildScriptBlock = false

  private val dependencyComparator = DependencyComparator(tokens)
  private val dependenciesByConfiguration = mutableMapOf<String, MutableList<DependencyDeclaration>>()
  private val dependenciesInOrder = mutableListOf<DependencyDeclaration>()
  private var alreadyOrdered = false

  private fun collectDependency(
    configuration: String,
    dependencyDeclaration: DependencyDeclaration
  ) {
    setIndent(dependencyDeclaration.declaration)
    dependenciesInOrder += dependencyDeclaration
    dependenciesByConfiguration.merge(configuration, mutableListOf(dependencyDeclaration)) { acc, inc ->
      acc.apply { addAll(inc) }
    }
  }

  private fun setIndent(ctx: ParserRuleContext) {
    if (smartIndentSet) return

    tokens.getHiddenTokensToLeft(ctx.start.tokenIndex, GradleGroovyScriptLexer.WHITESPACE)
      ?.firstOrNull()?.text?.replace("\n", "")?.let {
        smartIndentSet = true
        indent = it
      }
  }

  /**
   * Returns the sorted build script.
   *
   * Throws [BuildScriptParseException] if the script has some
   * idiosyncrasy that impairs parsing.
   *
   * Throws [AlreadyOrderedException] if the script is already
   * sorted correctly.
   */
  @Throws(BuildScriptParseException::class, AlreadyOrderedException::class)
  public fun rewritten(): String {
    errorListener.errorMessages.ifNotEmpty {
      throw BuildScriptParseException.withErrors(errorListener.errorMessages)
    }
    if (isSorted()) throw AlreadyOrderedException()

    return rewriter.text
  }

  /**
   * Returns `true` if this file's dependencies are already sorted correctly, or if there are no
   * dependencies.
   */
  public fun isSorted(): Boolean = alreadyOrdered || dependenciesByConfiguration.isEmpty()

  /** Returns `true` if there were errors parsing the build script. */
  public fun hasParseErrors(): Boolean = errorListener.errorMessages.isNotEmpty()

  /** Returns the parse exception if there is one, otherwise null. */
  public fun getParseError(): BuildScriptParseException? {
    return if (errorListener.errorMessages.isNotEmpty()) {
      BuildScriptParseException.withErrors(errorListener.errorMessages)
    } else {
      null
    }
  }

  override fun enterBuildscript(ctx: GradleGroovyScript.BuildscriptContext?) {
    isInBuildScriptBlock = true
  }

  override fun exitBuildscript(ctx: GradleGroovyScript.BuildscriptContext?) {
    isInBuildScriptBlock = false
  }

  override fun enterNormalDeclaration(ctx: NormalDeclarationContext) {
    if (isInBuildScriptBlock) return
    collectDependency(tokens.getText(ctx.configuration()), DependencyDeclaration.of(ctx, filePath))
  }

  override fun enterPlatformDeclaration(ctx: PlatformDeclarationContext) {
    if (isInBuildScriptBlock) return
    collectDependency(tokens.getText(ctx.configuration()), DependencyDeclaration.of(ctx, filePath))
  }

  override fun enterTestFixturesDeclaration(ctx: TestFixturesDeclarationContext) {
    if (isInBuildScriptBlock) return
    collectDependency(tokens.getText(ctx.configuration()), DependencyDeclaration.of(ctx, filePath))
  }

  override fun exitDependencies(ctx: DependenciesContext) {
    if (isInBuildScriptBlock) return
    rewriter.replace(ctx.start, ctx.stop, dependenciesBlock())
  }

  private fun dependenciesBlock() = buildString {
    val newOrder = mutableListOf<DependencyDeclaration>()

    appendLine("dependencies {")
    dependenciesByConfiguration.entries.sortedWith(ConfigurationComparator)
      .forEachIndexed { i, entry ->
        if (i != 0) appendLine()
        data class Texts(val comment: String?, val declarationText: String)
        entry.value.sortedWith(dependencyComparator)
          .map { dependency ->
            dependency to Texts(
              comment = tokens.getHiddenTokensToLeft(dependency.declaration.start.tokenIndex, GradleGroovyScriptLexer.COMMENTS)
                ?.joinToString(separator = "") { "$indent${it.text}" }?.trimEnd(),
              declarationText = tokens.getText(dependency.declaration),
            )
          }
          .distinctBy { (_, texts) -> texts }
          .forEach { (declaration, texts) ->
            newOrder += declaration

            // Get preceding comments
            if (texts.comment != null) appendLine(texts.comment)

            append(indent)
            appendLine(texts.declarationText)
          }
      }
    append("}")

    // Check if the new ordering matches the old ordering. If so, we shouldn't rewrite the file.
    alreadyOrdered = isSameOrder(dependenciesInOrder, newOrder)
  }

  private fun isSameOrder(
    first: List<DependencyDeclaration>,
    second: List<DependencyDeclaration>
  ): Boolean {
    if (first.size != second.size) return false
    return first.zip(second).all { (l, r) ->
      tokens.getText(l.declaration) == tokens.getText(r.declaration)
    }
  }

  public companion object {
    @JvmStatic
    public fun sorterFor(file: Path): Sorter {
      val input = Files.newInputStream(file, StandardOpenOption.READ).use {
        CharStreams.fromStream(it)
      }
      val lexer = GradleGroovyScriptLexer(input)
      val tokens = CommonTokenStream(lexer)
      val parser = GradleGroovyScript(tokens)

      // Remove default error listeners to prevent insane console output
      lexer.removeErrorListeners()
      parser.removeErrorListeners()

      val errorListener = RewriterErrorListener()
      parser.addErrorListener(errorListener)
      lexer.addErrorListener(errorListener)

      val walker = ParseTreeWalker()
      val listener = Sorter(
        tokens = tokens,
        rewriter = TokenStreamRewriter(tokens),
        errorListener = errorListener,
        filePath = file.absolutePathString()
      )
      val tree = parser.script()
      walker.walk(listener, tree)

      return listener
    }
  }
}

internal class RewriterErrorListener : AbstractErrorListener() {
  val errorMessages = mutableListOf<String>()

  override fun syntaxError(
    recognizer: Recognizer<*, *>,
    offendingSymbol: Any,
    line: Int,
    charPositionInLine: Int,
    msg: String,
    e: RecognitionException?
  ) {
    errorMessages.add(msg)
  }
}

private inline fun <C> C.ifNotEmpty(block: (C) -> Unit) where C : Collection<*> {
  if (isNotEmpty()) {
    block(this)
  }
}
