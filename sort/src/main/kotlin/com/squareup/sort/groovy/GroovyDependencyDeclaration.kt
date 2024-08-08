package com.squareup.sort.groovy

import com.autonomousapps.grammar.gradle.GradleScript.DependencyContext
import com.autonomousapps.grammar.gradle.GradleScript.NormalDeclarationContext
import com.autonomousapps.grammar.gradle.GradleScript.PlatformDeclarationContext
import com.autonomousapps.grammar.gradle.GradleScript.QuoteContext
import com.autonomousapps.grammar.gradle.GradleScript.TestFixturesDeclarationContext
import com.squareup.sort.DependencyDeclaration
import org.antlr.v4.runtime.ParserRuleContext

/**
 * To sort a dependency declaration, we care what kind of declaration it is ("normal", "platform", "test fixtures"), as
 * well as what kind of dependency it is (GAV, project, file/files, catalog-like).
 */
internal class GroovyDependencyDeclaration(
  val declaration: ParserRuleContext,
  private val dependency: DependencyContext,
  private val declarationKind: DeclarationKind,
  private val dependencyKind: DependencyKind,
) : DependencyDeclaration {

  enum class DeclarationKind {
    NORMAL, PLATFORM, TEST_FIXTURES
  }

  enum class DependencyKind {
    NORMAL, PROJECT, FILE;

    companion object {
      fun of(dependency: DependencyContext, filePath: String): DependencyKind {
        return if (dependency.externalDependency() != null) NORMAL
        else if (dependency.projectDependency() != null) PROJECT
        else if (dependency.fileDependency() != null) FILE
        else error("Unknown dependency kind. Was <${dependency.text}> for $filePath")
      }
    }
  }

  override fun fullText(): String {
    throw UnsupportedOperationException("Use tokens.getText(dependency.declaration) instead")
  }

  override fun precedingComment(): String? {
    throw UnsupportedOperationException("Use precedingComment() instead")
  }

  override fun isPlatformDeclaration() = declarationKind == DeclarationKind.PLATFORM
  override fun isTestFixturesDeclaration() = declarationKind == DeclarationKind.TEST_FIXTURES

  override fun isProjectDependency() = dependencyKind == DependencyKind.PROJECT
  override fun isFileDependency() = dependencyKind == DependencyKind.FILE

  override fun hasQuotes(): Boolean {
    val i = declaration.children.indexOf(dependency)
    return declaration.getChild(i - 1) is QuoteContext && declaration.getChild(i + 1) is QuoteContext
  }

  override fun comparisonText(): String {
    val text = when {
      isProjectDependency() -> with(dependency.projectDependency()) {
        // If project(path: 'foo') syntax is used, take the path value.
        // Else, if project('foo') syntax is used, take the ID.
        projectMapEntry().firstOrNull { it.key.text == "path:" }?.value?.text
          ?: ID().text
      }

      isFileDependency() -> dependency.fileDependency().ID().text
      else -> dependency.externalDependency().ID().text
    }

    return text.replaceHyphens()
  }

  companion object {
    fun of(declaration: ParserRuleContext, filePath: String): GroovyDependencyDeclaration {
      val (dependency, declarationKind) = when (declaration) {
        is NormalDeclarationContext -> declaration.dependency() to DeclarationKind.NORMAL
        is PlatformDeclarationContext -> declaration.dependency() to DeclarationKind.PLATFORM
        is TestFixturesDeclarationContext -> declaration.dependency() to DeclarationKind.TEST_FIXTURES
        else -> error("Unknown declaration kind. Was ${declaration.text}.")
      }

      val dependencyKind = when (declaration) {
        is NormalDeclarationContext -> DependencyKind.of(declaration.dependency(), filePath)
        is PlatformDeclarationContext -> DependencyKind.of(declaration.dependency(), filePath)
        is TestFixturesDeclarationContext -> DependencyKind.of(declaration.dependency(), filePath)
        else -> error("Unknown declaration kind. Was ${declaration.text}.")
      }

      return GroovyDependencyDeclaration(declaration, dependency, declarationKind, dependencyKind)
    }
  }
}
