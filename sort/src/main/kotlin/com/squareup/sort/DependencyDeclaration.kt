package com.squareup.sort

import com.squareup.grammar.GradleGroovyScript.DependencyContext
import com.squareup.grammar.GradleGroovyScript.NormalDeclarationContext
import com.squareup.grammar.GradleGroovyScript.PlatformDeclarationContext
import com.squareup.grammar.GradleGroovyScript.TestFixturesDeclarationContext
import org.antlr.v4.runtime.ParserRuleContext

/**
 * To sort a dependency declaration, we care what kind of declaration it is ("normal", "platform", "test fixtures"), as
 * well as what kind of dependency it is (GAV, project, file/files, catalog-like).
 */
internal class DependencyDeclaration(
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
      fun of(dependency: DependencyContext, filePath: String): DependencyKind {
        return if (dependency.externalDependency() != null) NORMAL
        else if (dependency.projectDependency() != null) PROJECT
        else if (dependency.fileDependency() != null) FILE
        else error("Unknown dependency kind. Was <${dependency.text}> for $filePath")
      }
    }
  }

  fun isPlatformDeclaration() = declarationKind == DeclarationKind.PLATFORM
  fun isTestFixturesDeclaration() = declarationKind == DeclarationKind.TEST_FIXTURES

  fun isProjectDependency() = dependencyKind == DependencyKind.PROJECT
  fun isFileDependency() = dependencyKind == DependencyKind.FILE

  companion object {
    fun of(declaration: ParserRuleContext, filePath: String): DependencyDeclaration {
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

      return DependencyDeclaration(declaration, dependency, declarationKind, dependencyKind)
    }
  }
}