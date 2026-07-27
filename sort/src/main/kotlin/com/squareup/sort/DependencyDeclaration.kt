package com.squareup.sort

internal interface DependencyDeclaration {

  fun fullText(): String
  fun precedingComment(): String?

  fun isEnforcedPlatformDeclaration(): Boolean
  fun isPlatformDeclaration(): Boolean
  fun isTestFixturesDeclaration(): Boolean

  /** TODO(tsr): what about files and fileTree? */
  fun isFileDependency(): Boolean

  fun isGradleDistributionDependency(): Boolean

  fun isProjectDependency(): Boolean

  /**
   * Returns `true` if the dependency component is surrounded by quotation marks. Consider:
   * 1. implementation deps.foo // no quotes
   * 2. implementation 'com.foo:bar:1.0' // quotes
   *
   * We want 2 to be sorted above 1. This is arbitrary.
   */
  fun hasQuotes(): Boolean

  /**
   * Used by [DependencyComparator][com.squareup.sort.DependencyComparator] to sort declarations. We
   * sort by identifier within a configuration.
   */
  fun comparisonText(): String

  /**
   * Colons in external dependency coordinates should sort "higher" than hyphens. The comma's ASCII
   * value is 44, the hyphen's is 45, and the colon's is 58. We replace those colons with commas and
   * then rely on natural sort order from there.
   *
   * For example, consider 'foo-bar:baz' vs. 'foo:bar:baz'. Before this transformation,
   * 'foo-bar:baz' will appear before 'foo:bar:baz'. But after it, we compare 'foo-bar,baz' to
   * 'foo,bar,baz', which gives the desired sort ordering.
   *
   * Project path colons remain unchanged so nested paths sort after hyphenated sibling paths.
   * Similarly, single and double quotes have different ASCII values, but we don't care about that
   * for our purposes.
   */
  fun String.replaceHyphens(): String {
    // TODO maybe I should make this an ABC and this function protected.
    val normalizedQuotes = replace("'", "\"")
    return if (this@DependencyDeclaration.isProjectDependency()) {
      normalizedQuotes
    } else {
      normalizedQuotes.replace(':', ',')
    }
  }
}
