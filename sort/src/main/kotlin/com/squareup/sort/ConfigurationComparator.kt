package com.squareup.sort

internal object ConfigurationComparator :
  Comparator<MutableMap.MutableEntry<String, MutableList<DependencyDeclaration>>> {

  private class Configuration(
    private val configuration: String,
    val level: Int,
    /**
     * Android support. A "variant" configuration looks like "debugApi", "releaseImplementation", etc.
     * The variant will be "debug", "release", etc.
     */
    var variant: String? = null
  ) {

    companion object {
      val values = listOf(
        "api" to { Configuration("api", 0) },
        "implementation" to { Configuration("implementation", 1) },
        "compileOnlyApi" to { Configuration("compileOnlyApi", 2) },
        "compileOnly" to { Configuration("compileOnly", 3) },
        "runtimeOnly" to { Configuration("runtimeOnly", 4) },
        "annotationProcessor" to { Configuration("annotationProcessor", 5) },
        "kapt" to { Configuration("kapt", 6) },
        "testImplementation" to { Configuration("testImplementation", 7) },
        "testCompileOnly" to { Configuration("testCompileOnly", 8) },
        "testRuntimeOnly" to { Configuration("testRuntimeOnly", 9) },
        "androidTestImplementation" to { Configuration("androidTestImplementation", 10) },
      )

      fun of(configuration: String): Configuration? {
        fun findConfiguration(
          predicate: (Pair<String, () -> Configuration>) -> Boolean
        ): Configuration? {
          return values.find(predicate)?.second?.invoke()
        }

        // Try to find an exact match
        var matchingConfiguration = findConfiguration { it.first == configuration }

        // If that failed, look for a variant
        if (matchingConfiguration == null) {
          matchingConfiguration = findConfiguration { configuration.endsWith(it.first, true) }
          if (matchingConfiguration != null) {
            matchingConfiguration.variant = configuration.substring(
              0,
              configuration.length - matchingConfiguration.configuration.length
            )
          }
        }

        // Look for a variant again
        if (matchingConfiguration == null) {
          matchingConfiguration = findConfiguration { configuration.startsWith(it.first, true) }
          if (matchingConfiguration != null) {
            matchingConfiguration.variant = configuration.substring(
              configuration.length - matchingConfiguration.configuration.length,
              configuration.length
            )
          }
        }

        return matchingConfiguration
      }
    }
  }

  override fun compare(
    left: MutableMap.MutableEntry<String, MutableList<DependencyDeclaration>>,
    right: MutableMap.MutableEntry<String, MutableList<DependencyDeclaration>>
  ): Int = stringCompare(left.key, right.key)

  /** Visible for testing. */
  @JvmStatic
  fun stringCompare(
    left: String,
    right: String
  ): Int {
    val leftC = Configuration.of(left)
    val rightC = Configuration.of(right)

    // Null means they don't map to a known configuration. So, compare by String natural order.
    if (leftC == null && rightC == null) return left.compareTo(right)
    // Unknown configuration is "higher than" known
    if (rightC == null) return 1
    if (leftC == null) return -1

    val c = leftC.level.compareTo(rightC.level)

    // If each maps to a known configuration, and they're different, we can return that value
    if (c != 0) return c
    // If each maps to the same configuration, we now differentiate based on whether variants are
    // involved. Non-variants are "higher than" variants.
    if (leftC.variant != null && rightC.variant != null) {
      return rightC.variant!!.compareTo(leftC.variant!!)
    }
    return if (rightC.variant != null) return -1 else 1
  }
}
