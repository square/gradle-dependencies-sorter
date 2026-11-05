package com.squareup.sort

/**
 * Writes configurations in Gradle order and dependencies in comparator order, inserting configured blank lines and
 * dropping entries with the same rendered declaration and comment.
 *
 * The returned declarations let each parser compare the emitted order with the original order it collected.
 */
internal fun <D : DependencyDeclaration> StringBuilder.appendSortedDependencies(
  dependenciesByConfiguration: Map<String, List<D>>,
  dependencyComparator: Comparator<DependencyDeclaration>,
  bodyIndent: String,
  insertBlankLines: Boolean,
  textsFor: (D) -> Texts,
): List<D> {
  val emitted = mutableListOf<D>()
  dependenciesByConfiguration.entries
    .sortedWith { left, right -> Configuration.stringCompare(left.key, right.key) }
    .forEachIndexed { index, entry ->
      if (index != 0 && insertBlankLines) appendLine()

      entry.value.sortedWith(dependencyComparator)
        .map { dependency -> dependency to textsFor(dependency) }
        .distinctBy { (_, texts) -> texts }
        .forEach { (dependency, texts) ->
          emitted += dependency
          if (texts.comment != null) appendLine(texts.comment.replace("\r", ""))
          append(bodyIndent.replace("\r", ""))
          appendLine(texts.declarationText.replace("\r", ""))
        }
    }
  return emitted
}
