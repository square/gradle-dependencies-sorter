package com.squareup.sort

/** Tracks the original and rewritten dependency order for each `dependencies` block. */
internal class Ordering<T : DependencyDeclaration>(
  private val sameDependency: (T, T) -> Boolean = { first, second -> first == second },
) {

  private val dependenciesInOrder = mutableListOf<T>()
  private val orderedBlocks = mutableListOf<Boolean>()

  fun isAlreadyOrdered(): Boolean = orderedBlocks.all { it }

  fun add(dependency: T) {
    dependenciesInOrder += dependency
  }

  fun addAll(dependencies: List<T>) {
    dependenciesInOrder += dependencies
  }

  /**
   * Checks ordering as we leave a dependencies block, then clears the collected order so the next block is checked
   * separately.
   */
  fun checkOrdering(newOrder: List<T>): Boolean {
    val isAlreadyOrdered = dependenciesInOrder.size == newOrder.size &&
      dependenciesInOrder.zip(newOrder).all { (first, second) -> sameDependency(first, second) }
    orderedBlocks += isAlreadyOrdered
    dependenciesInOrder.clear()
    return isAlreadyOrdered
  }
}
