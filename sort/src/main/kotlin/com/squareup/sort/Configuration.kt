package com.squareup.sort

internal class Configuration(
  private val configurationName: String,
  val level: Int,
  /**
   * Android support. A "variant" configuration looks like "debugApi", "releaseImplementation", etc.
   * The variant will be "debug", "release", etc.
   */
  var variant: String? = null
) {

  companion object {
    private val values = listOf(
      "api" to { Configuration("api", 0) },
      "implementation" to { Configuration("implementation", 1) },
      "compileOnlyApi" to { Configuration("compileOnlyApi", 2) },
      "compileOnly" to { Configuration("compileOnly", 3) },
      "runtimeOnly" to { Configuration("runtimeOnly", 4) },
      "annotationProcessor" to { Configuration("annotationProcessor", 5) },
      "kapt" to { Configuration("kapt", 6) },
      "testFixturesApi" to { Configuration("testFixturesApi", 7) },
      "testFixturesImplementation" to { Configuration("testFixturesImplementation", 8) },
      "testImplementation" to { Configuration("testImplementation", 9) },
      "testCompileOnly" to { Configuration("testCompileOnly", 10) },
      "testRuntimeOnly" to { Configuration("testRuntimeOnly", 11) },
      "androidTestImplementation" to { Configuration("androidTestImplementation", 12) },
    )

    fun of(configurationName: String): Configuration? {
      fun findConfiguration(
        predicate: (Pair<String, () -> Configuration>) -> Boolean
      ): Configuration? = values.find(predicate)?.second?.invoke()

      // Try to find an exact match
      var matchingConfiguration = findConfiguration { it.first == configurationName }

      // If that failed, look for a variant
      if (matchingConfiguration == null) {
        matchingConfiguration = findConfiguration { configurationName.endsWith(it.first, true) }
        if (matchingConfiguration != null) {
          matchingConfiguration.variant = configurationName.substring(
            0,
            configurationName.length - matchingConfiguration.configurationName.length
          )
        }
      }

      // Look for a variant again
      if (matchingConfiguration == null) {
        matchingConfiguration = findConfiguration { configurationName.startsWith(it.first, true) }
        if (matchingConfiguration != null) {
          matchingConfiguration.variant = configurationName.substring(
            configurationName.length - matchingConfiguration.configurationName.length,
            configurationName.length
          )
        }
      }

      return matchingConfiguration
    }

    @JvmStatic
    fun stringCompare(
      left: String,
      right: String
    ): Int {
      val leftC = of(left)
      val rightC = of(right)

      // Null means they don't map to a known configuration. So, compare by String natural order.
      if (leftC == null && rightC == null) {
        // In Kotlin DSL, custom configurations can be like `"functionalTest"(foo)`. We want to sort ignoring the
        // quotation marks.
        return left.removeSurrounding("\"").compareTo(right.removeSurrounding("\""))
      }
      // Unknown configuration is "lower than" known
      if (rightC == null) return -1
      if (leftC == null) return 1

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
}
