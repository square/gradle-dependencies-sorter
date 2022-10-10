package com.squareup.sort

import com.squareup.grammar.GradleGroovyScript
import com.squareup.grammar.GradleGroovyScript.DependenciesContext
import com.squareup.grammar.GradleGroovyScript.DependencyContext
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

public class Sorter private constructor(
  private val tokens: CommonTokenStream,
  private val rewriter: TokenStreamRewriter,
  private val errorListener: RewriterErrorListener,
) : GradleGroovyScriptBaseListener() {

  // We use a default of two spaces, but update it at most once later on.
  private var smartIndentSet = false
  private var indent = "  "

  private val dependencyComparator = DependencyComparator(tokens)
  private val dependenciesByConfiguration = mutableMapOf<String, MutableList<CtxDependency>>()
  private val dependenciesInOrder = mutableListOf<CtxDependency>()
  private var alreadyOrdered = false

  private fun collectDependency(
    configuration: String,
    ctxDependency: CtxDependency
  ) {
    setIndent(ctxDependency.declaration)
    dependenciesInOrder += ctxDependency
    dependenciesByConfiguration.merge(configuration, mutableListOf(ctxDependency)) { acc, inc ->
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

  override fun enterNormalDeclaration(ctx: NormalDeclarationContext) {
    val c = ctx.configuration()
    collectDependency(tokens.getText(c), CtxDependency.of(ctx))
  }

  override fun enterPlatformDeclaration(ctx: PlatformDeclarationContext) {
    val c = ctx.configuration()
    collectDependency(tokens.getText(c), CtxDependency.of(ctx))
  }

  override fun enterTestFixturesDeclaration(ctx: TestFixturesDeclarationContext) {
    val c = ctx.configuration()
    collectDependency(tokens.getText(c), CtxDependency.of(ctx))
  }

  override fun exitDependencies(ctx: DependenciesContext) {
    rewriter.replace(ctx.start, ctx.stop, dependenciesBlock())
  }

  private fun dependenciesBlock() = buildString {
    val newOrder = mutableListOf<CtxDependency>()

    appendLine("dependencies {")
    dependenciesByConfiguration.entries.sortedWith(ConfigurationComparator)
      .forEachIndexed { i, entry ->
        if (i != 0) appendLine()

        val declarations = entry.value.sortedWith(dependencyComparator)
        declarations.forEach { declaration ->
          newOrder += declaration

          // Get preceding comments
          val start = declaration.declaration.start.tokenIndex
          tokens.getHiddenTokensToLeft(start, GradleGroovyScriptLexer.COMMENTS)?.let { comments ->
            appendLine(comments.joinToString(separator = "") { "$indent${it.text}" }.trimEnd())
          }

          append(indent)
          appendLine(tokens.getText(declaration.declaration))
        }
      }
    append("}")

    // Check if the new ordering matches the old ordering. If so, we shouldn't rewrite the file.
    alreadyOrdered = isSameOrder(dependenciesInOrder, newOrder)
  }

  private fun isSameOrder(
    first: List<CtxDependency>,
    second: List<CtxDependency>
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

      val errorListener = RewriterErrorListener()
      parser.addErrorListener(errorListener)

      val walker = ParseTreeWalker()
      val listener = Sorter(
        tokens = tokens,
        rewriter = TokenStreamRewriter(tokens),
        errorListener = errorListener
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

/**
 * To sort a dependency declaration, we care what kind of declaration it is ("normal", "platform", "test fixtures"), as
 * well as what kind of dependency it is (GAV, project, file/files, catalog-like).
 */
internal class CtxDependency(
  val declaration: ParserRuleContext,
  val dependency: DependencyContext,
  private val declarationKind: DeclarationKind,
  private val dependencyKind: DependencyKind,
) {

  enum class DeclarationKind {
    NORMAL, PLATFORM, TEST_FIXTURES
  }

  enum class DependencyKind {
    NORMAL, PROJECT, FILE;

    companion object {
      fun of(dependency: DependencyContext): DependencyKind {
        return if (dependency.externalDependency() != null) NORMAL
        else if (dependency.projectDependency() != null) PROJECT
        else if (dependency.fileDependency() != null) FILE
        else error("Unknown dependency kind. Was ${dependency.text}.")
      }
    }
  }

  fun isPlatformDeclaration() = declarationKind == DeclarationKind.PLATFORM
  fun isTestFixturesDeclaration() = declarationKind == DeclarationKind.TEST_FIXTURES

  fun isProjectDependency() = dependencyKind == DependencyKind.PROJECT
  fun isFileDependency() = dependencyKind == DependencyKind.FILE

  companion object {
    fun of(declaration: ParserRuleContext): CtxDependency {
      val (dependency, declarationKind) = when (declaration) {
        is NormalDeclarationContext -> declaration.dependency() to DeclarationKind.NORMAL
        is PlatformDeclarationContext -> declaration.dependency() to DeclarationKind.PLATFORM
        is TestFixturesDeclarationContext -> declaration.dependency() to DeclarationKind.TEST_FIXTURES
        else -> error("Unknown declaration kind. Was ${declaration.text}.")
      }

      val dependencyKind = when (declaration) {
        is NormalDeclarationContext -> DependencyKind.of(declaration.dependency())
        is PlatformDeclarationContext -> DependencyKind.of(declaration.dependency())
        is TestFixturesDeclarationContext -> DependencyKind.of(declaration.dependency())
        else -> error("Unknown declaration kind. Was ${declaration.text}.")
      }

      return CtxDependency(declaration, dependency, declarationKind, dependencyKind)
    }
  }
}

private inline fun <C> C.ifNotEmpty(block: (C) -> Unit) where C : Collection<*> {
  if (isNotEmpty()) {
    block(this)
  }
}
