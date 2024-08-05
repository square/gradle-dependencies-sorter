package com.squareup.sort

internal class DependencyComparator : Comparator<DependencyDeclaration> {

  override fun compare(
    left: DependencyDeclaration,
    right: DependencyDeclaration,
  ): Int {
    if (left.isPlatformDeclaration() && right.isPlatformDeclaration()) return compareDeclaration(
      left,
      right
    )
    if (left.isPlatformDeclaration()) return -1
    if (right.isPlatformDeclaration()) return 1

    if (left.isTestFixturesDeclaration() && right.isTestFixturesDeclaration()) return compareDeclaration(
      left,
      right
    )
    if (left.isTestFixturesDeclaration()) return -1
    if (right.isTestFixturesDeclaration()) return 1

    return compareDeclaration(left, right)
  }

  private fun compareDeclaration(
    left: DependencyDeclaration,
    right: DependencyDeclaration,
  ): Int {
    if (left.isProjectDependency() && right.isProjectDependency()) return compareDependencies(
      left,
      right
    )
    if (left.isProjectDependency()) return -1
    if (right.isProjectDependency()) return 1

    if (left.isFileDependency() && right.isFileDependency()) return compareDependencies(left, right)
    if (left.isFileDependency()) return -1
    if (right.isFileDependency()) return -1

    return compareDependencies(left, right)
  }

  private fun compareDependencies(
    left: DependencyDeclaration,
    right: DependencyDeclaration,
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
}
