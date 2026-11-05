package com.squareup.sort.groovy

import com.autonomousapps.grammar.gradle.GradleScript
import com.autonomousapps.grammar.gradle.GradleScript.BlockContext
import com.autonomousapps.grammar.gradle.GradleScript.BuildscriptContext
import com.autonomousapps.grammar.gradle.GradleScript.ClosureContext
import com.autonomousapps.grammar.gradle.GradleScript.DependenciesContext
import com.autonomousapps.grammar.gradle.GradleScript.EnforcedPlatformDeclarationContext
import com.autonomousapps.grammar.gradle.GradleScript.NormalDeclarationContext
import com.autonomousapps.grammar.gradle.GradleScript.PlatformDeclarationContext
import com.autonomousapps.grammar.gradle.GradleScript.ScriptContext
import com.autonomousapps.grammar.gradle.GradleScript.TestFixturesDeclarationContext
import com.autonomousapps.grammar.gradle.GradleScriptBaseListener
import com.autonomousapps.grammar.gradle.GradleScriptLexer
import com.squareup.parse.AbstractErrorListener
import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.DependencyComparator
import com.squareup.sort.Ordering
import com.squareup.sort.RewrittenBlock
import com.squareup.sort.RewrittenBlocks
import com.squareup.sort.Sorter
import com.squareup.sort.Texts
import com.squareup.sort.appendSortedDependencies
import com.squareup.sort.matchesSortableBlock
import com.squareup.sort.sortableBlockPaths
import com.squareup.utils.ifNotEmpty
import org.antlr.v4.runtime.CharStream
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

public class GroovySorter private constructor(
  input: CharStream,
  private val tokens: CommonTokenStream,
  private val errorListener: RewriterErrorListener,
  private val filePath: String,
  private val config: Sorter.Config,
  private val lineSeparator: String,
) : Sorter, GradleScriptBaseListener() {

  private val rewriter = TokenStreamRewriter(tokens)
  private val source = GroovySource(input)
  private val dependencyComparator = DependencyComparator()
  private val dependencyBlocks = ArrayDeque<MutableDependencies>()
  private val buildscriptBlocks = ArrayDeque<Boolean>()
  // Empty frames reset path matching across anonymous closures and parser blocks found in non-code source.
  private val blockPathStack = ArrayDeque<List<String>>()
  private val rewrittenBlocks = RewrittenBlocks(source::text)
  private val sortableBlockPaths = config.sortableBlockPaths()
  private val blockRewriter = GroovyBlockRewriter(source) { start, stop, replacements ->
    rewrittenBlocks.render(start, stop, replacements)
  }
  private val isInBuildScriptBlock: Boolean
    get() = buildscriptBlocks.any { it }

  /**
   * Returns the sorted build script.
   *
   * Throws [BuildScriptParseException] if the script has some idiosyncrasy that impairs parsing.
   *
   * Throws [AlreadyOrderedException] if the script is already sorted correctly.
   */
  @Throws(BuildScriptParseException::class, AlreadyOrderedException::class)
  override fun rewritten(): String {
    errorListener.errorMessages.ifNotEmpty {
      throw BuildScriptParseException.withErrors(errorListener.errorMessages)
    }
    if (isSorted()) throw AlreadyOrderedException()

    return rewriter.text
  }

  /** Returns `true` if this file's sortable blocks are already ordered, or if there are none. */
  override fun isSorted(): Boolean = rewrittenBlocks.isAlreadyOrdered

  /** Returns `true` if there were errors parsing the build script. */
  override fun hasParseErrors(): Boolean = errorListener.errorMessages.isNotEmpty()

  /** Returns the parse exception if there is one, otherwise null. */
  override fun getParseError(): BuildScriptParseException? {
    return if (errorListener.errorMessages.isNotEmpty()) {
      BuildScriptParseException.withErrors(errorListener.errorMessages)
    } else {
      null
    }
  }

  override fun enterBuildscript(ctx: BuildscriptContext) {
    val isValid = isCodeBlock(ctx, ctx.BRACE_OPEN().symbol.startIndex)
    buildscriptBlocks.addLast(isValid)
    blockPathStack.addLast(if (isValid) listOf("buildscript") else emptyList())
  }

  override fun exitBuildscript(ctx: BuildscriptContext) {
    blockPathStack.removeLast()
    buildscriptBlocks.removeLast()
  }

  override fun enterDependencies(ctx: DependenciesContext) {
    val isValid = source.isCode(ctx.start.startIndex) && source.isCode(ctx.stop.stopIndex)
    dependencyBlocks.addLast(
      MutableDependencies(
        isValid = isValid,
        ordering = Ordering { first, second ->
          tokens.getText(first.declaration) == tokens.getText(second.declaration)
        },
      )
    )
    blockPathStack.addLast(
      if (isValid) {
        ctx.start.text.substringBeforeLast('{').trim().split('.')
      } else {
        emptyList()
      }
    )
  }

  override fun exitDependencies(ctx: DependenciesContext) {
    val dependencies = dependencyBlocks.removeLast()
    if (dependencies.isValid && !isInBuildScriptBlock) {
      rewrittenBlocks[ctx] = dependenciesBlock(ctx, dependencies)
    }
    blockPathStack.removeLast()
  }

  override fun enterBlock(ctx: BlockContext) {
    val isValid = isCodeBlock(ctx, ctx.BRACE_OPEN().symbol.startIndex)
    blockPathStack.addLast(
      if (isValid) ctx.ID().text.split('.') else emptyList()
    )
  }

  override fun enterClosure(ctx: ClosureContext) {
    blockPathStack.addLast(emptyList())
  }

  override fun exitClosure(ctx: ClosureContext) {
    blockPathStack.removeLast()
  }

  override fun exitBlock(ctx: BlockContext) {
    val isValid = blockPathStack.last().isNotEmpty()
    if (isValid && sortableBlockPaths.matchesSortableBlock(blockPathStack)) {
      rewrittenBlocks[ctx] = blockRewriter.rewrite(ctx)
    }
    blockPathStack.removeLast()
  }

  override fun enterNormalDeclaration(ctx: NormalDeclarationContext): Unit =
    collectDependency(tokens.getText(ctx.configuration()), ctx)

  override fun enterEnforcedPlatformDeclaration(ctx: EnforcedPlatformDeclarationContext): Unit =
    collectDependency(tokens.getText(ctx.configuration()), ctx)

  override fun enterPlatformDeclaration(ctx: PlatformDeclarationContext): Unit =
    collectDependency(tokens.getText(ctx.configuration()), ctx)

  override fun enterTestFixturesDeclaration(ctx: TestFixturesDeclarationContext): Unit =
    collectDependency(tokens.getText(ctx.configuration()), ctx)

  override fun exitScript(ctx: ScriptContext) {
    rewrittenBlocks.applyTo(rewriter)
  }

  /** Adds a declaration to the innermost real dependencies block unless it belongs to `buildscript`. */
  private fun collectDependency(configuration: String, declaration: ParserRuleContext) {
    val current = dependencyBlocks.lastOrNull() ?: return
    if (!current.isValid || isInBuildScriptBlock) return

    val dependency = GroovyDependencyDeclaration.of(declaration, filePath)
    current.ordering.add(dependency)
    current.dependenciesByConfiguration
      .getOrPut(configuration) { mutableListOf() }
      .add(dependency)
  }

  /** Rejects parser blocks whose name, opening brace, or closing token came from a string or comment. */
  private fun isCodeBlock(ctx: ParserRuleContext, braceStartIndex: Int): Boolean {
    return source.isCode(ctx.start.startIndex) &&
      source.isCode(braceStartIndex) &&
      source.isCode(ctx.stop.stopIndex)
  }

  /** Rebuilds nested blocks before sorted dependency declarations, preserving local indentation and child rewrites. */
  private fun dependenciesBlock(
    ctx: DependenciesContext,
    dependencies: MutableDependencies,
  ): RewrittenBlock {
    val newOrder = mutableListOf<GroovyDependencyDeclaration>()
    val nestedBlocks = ctx.block().filter { block ->
      source.isCode(block.start.startIndex) && source.isCode(block.stop.stopIndex)
    }

    // Blocks can be nested inside any DSL, so derive indentation from this block's source text.
    val blockIndent = indentationBefore(ctx.start.tokenIndex).orEmpty()
    val firstDeclarationToken = dependencies.dependenciesByConfiguration.values.asSequence()
      .flatten()
      .minByOrNull { it.declaration.start.tokenIndex }
      ?.declaration
      ?.start
      ?.tokenIndex
    val firstBlockToken = nestedBlocks.minOfOrNull { it.start.tokenIndex }
    val firstBodyToken = listOfNotNull(firstDeclarationToken, firstBlockToken).minOrNull()
    val bodyIndent = firstBodyToken
      ?.let(::indentationBefore)
      ?: "$blockIndent  "
    val text = buildString {
      appendLine("${ctx.start.text.substringBeforeLast('{').trimEnd()} {")

      nestedBlocks.forEach { block ->
        precedingComment(block.start.tokenIndex, bodyIndent)?.let { comment ->
          appendLine(comment.replace("\r", ""))
        }
        append(bodyIndent.replace("\r", ""))
        appendLine(rewrittenBlocks.render(block).replace("\r", ""))
      }
      if (nestedBlocks.isNotEmpty() && dependencies.dependenciesByConfiguration.isNotEmpty()) {
        appendLine()
      }

      newOrder += appendSortedDependencies(
        dependenciesByConfiguration = dependencies.dependenciesByConfiguration,
        dependencyComparator = dependencyComparator,
        bodyIndent = bodyIndent,
        insertBlankLines = config.insertBlankLines,
      ) { dependency ->
        Texts(
          comment = precedingComment(dependency.declaration.start.tokenIndex, bodyIndent),
          declarationText = rewrittenBlocks.render(dependency.declaration),
        )
      }
      append(blockIndent)
      append("}")
    }.replace("\n", lineSeparator)

    return RewrittenBlock(
      text = text,
      isAlreadyOrdered = dependencies.ordering.checkOrdering(newOrder),
    )
  }

  /** Returns whitespace between the previous line break and [tokenIndex], or null if other text intervenes. */
  private fun indentationBefore(tokenIndex: Int): String? {
    return tokens.getHiddenTokensToLeft(tokenIndex, GradleScriptLexer.WHITESPACE)
      ?.lastOrNull()
      ?.text
      ?.substringAfterLast('\n')
      ?.substringAfterLast('\r')
      ?.takeIf { it.all { char -> char == ' ' || char == '\t' } }
  }

  /** Returns comments attached to [tokenIndex], prefixing each token with the destination [indent]. */
  private fun precedingComment(tokenIndex: Int, indent: String): String? {
    return tokens.getHiddenTokensToLeft(tokenIndex, GradleScriptLexer.COMMENTS)
      ?.joinToString(separator = "") {
        "$indent${it.text}"
      }
      ?.trimEnd()
  }

  public companion object {
    @JvmStatic
    @JvmOverloads
    public fun of(
      file: Path,
      config: Sorter.Config = Sorter.defaultConfig(),
      lineSeparator: String = System.lineSeparator(),
    ): GroovySorter {
      val input = Files.newInputStream(file, StandardOpenOption.READ).use {
        CharStreams.fromStream(it)
      }
      val lexer = GradleScriptLexer(input)
      val tokens = CommonTokenStream(lexer)
      val parser = GradleScript(tokens)

      // Remove default error listeners to prevent insane console output
      lexer.removeErrorListeners()
      parser.removeErrorListeners()

      val errorListener = RewriterErrorListener()
      parser.addErrorListener(errorListener)
      lexer.addErrorListener(errorListener)

      val walker = ParseTreeWalker()
      val listener = GroovySorter(
        input = input,
        tokens = tokens,
        errorListener = errorListener,
        filePath = file.absolutePathString(),
        config = config,
        lineSeparator = lineSeparator,
      )
      val tree = parser.script()
      walker.walk(listener, tree)

      return listener
    }
  }

  private class MutableDependencies(
    val isValid: Boolean,
    val ordering: Ordering<GroovyDependencyDeclaration>,
    val dependenciesByConfiguration: MutableMap<String, MutableList<GroovyDependencyDeclaration>> =
      mutableMapOf(),
  )
}

internal class RewriterErrorListener : AbstractErrorListener() {
  val errorMessages = mutableListOf<String>()

  override fun syntaxError(
    recognizer: Recognizer<*, *>,
    offendingSymbol: Any,
    line: Int,
    charPositionInLine: Int,
    msg: String,
    e: RecognitionException?,
  ) {
    errorMessages.add(msg)
  }
}
