package com.squareup.sort

import com.squareup.grammar.GradleGroovyScript.QuoteContext

internal class DependencyComparator : Comparator<DependencyDeclaration> {

  override fun compare(
    left: DependencyDeclaration,
    right: DependencyDeclaration
  ): Int {
    if (left.isPlatformDeclaration() && right.isPlatformDeclaration()) return compareDeclaration(left, right)
    if (left.isPlatformDeclaration()) return -1
    if (right.isPlatformDeclaration()) return 1

    if (left.isTestFixturesDeclaration() && right.isTestFixturesDeclaration()) return compareDeclaration(left, right)
    if (left.isTestFixturesDeclaration()) return -1
    if (right.isTestFixturesDeclaration()) return 1

    return compareDeclaration(left, right)
  }

  private fun compareDeclaration(
    left: DependencyDeclaration,
    right: DependencyDeclaration
  ): Int {
    if (left.isProjectDependency() && right.isProjectDependency()) return compareDependencies(left, right)
    if (left.isProjectDependency()) return -1
    if (right.isProjectDependency()) return 1

    if (left.isFileDependency() && right.isFileDependency()) return compareDependencies(left, right)
    if (left.isFileDependency()) return -1
    if (right.isFileDependency()) return -1

    return compareDependencies(left, right)
  }

  private fun compareDependencies(
    left: DependencyDeclaration,
    right: DependencyDeclaration
  ): Int {
    val leftText = left.comparisonText()
    val rightText = right.comparisonText()

    // Get natural sort order
    val c = leftText.compareTo(rightText)
    val leftQuotes = left.hasQuotes()
    val rightQuotes = right.hasQuotes()

    // Quoted dependencies sort higher
    if (leftQuotes && rightQuotes) return c
    if (leftQuotes) return -1
    if (rightQuotes) return 1

    // No quotes on either -> return natural sort order
    return c
  }

  /**
   * Returns `true` if the dependency component is surrounded by quotation marks. Consider:
   * 1. implementation deps.foo // no quotes
   * 2. implementation 'com.foo:bar:1.0' // quotes
   *
   * We want 2 to be sorted above 1. This is arbitrary.
   */
  private fun DependencyDeclaration.hasQuotes(): Boolean {
    val i = declaration.children.indexOf(dependency)
    return declaration.getChild(i - 1) is QuoteContext && declaration.getChild(i + 1) is QuoteContext
  }

  private fun DependencyDeclaration.comparisonText(): String {
    val text = when {
      isProjectDependency() -> with (dependency.projectDependency()) {
        // If project(path: 'foo') syntax is used, take the path value.
        // Else, if project('foo') syntax is used, take the ID.
        projectMapEntry().firstOrNull { it.key.text == "path:" }?.value?.text
          ?: ID().text
      }
      isFileDependency() -> dependency.fileDependency().ID().text
      else -> dependency.externalDependency().ID().text
    }
    // Colons should sort "higher" than hyphens. The comma's ascii value is 44, the hyphen's is
    // 45, and the colon's is 58. We replace colons with commas and then rely on natural sort order
    // from there. Similarly, single and double quotes have different values, but we don't care
    // about that for our purposes.
    return text.replace(':', ',').replace("'", "\"")
  }
}
