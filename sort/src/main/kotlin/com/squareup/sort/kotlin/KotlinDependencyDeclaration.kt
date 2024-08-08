package com.squareup.sort.kotlin

import cash.grammar.kotlindsl.model.DependencyDeclaration.Capability
import cash.grammar.kotlindsl.model.DependencyDeclaration.Type
import com.squareup.sort.DependencyDeclaration
import cash.grammar.kotlindsl.model.DependencyDeclaration as ModelDeclaration

internal class KotlinDependencyDeclaration(
  private val base: ModelDeclaration,
) : DependencyDeclaration {

  val configuration = base.configuration

  override fun fullText(): String = base.fullText

  override fun precedingComment(): String? = base.precedingComment

  override fun isPlatformDeclaration(): Boolean {
    return base.capability == Capability.PLATFORM
  }

  override fun isTestFixturesDeclaration(): Boolean {
    return base.capability == Capability.TEST_FIXTURES
  }

  override fun isFileDependency(): Boolean {
    return base.type == Type.FILE
  }

  override fun isProjectDependency(): Boolean {
    return base.type == Type.PROJECT
  }

  override fun hasQuotes(): Boolean {
    return base.identifier.path.startsWith("'") || base.identifier.path.startsWith("\"")
  }

  override fun comparisonText(): String {
    // TODO: this may not exactly match the Groovy DSL behavior
    return base.identifier.path.replaceHyphens()
  }

  override fun toString(): String {
    return fullText()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as KotlinDependencyDeclaration

    return base == other.base
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + base.hashCode()
    return result
  }
}
