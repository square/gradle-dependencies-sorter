package com.squareup.sort

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.BufferedReader
import javax.inject.Inject

/**
 * sortDependencies {
 *   // Defines a custom version of the SortDependencies CLI to use
 *   version.set(/* custom version */)
 *
 *   // When true, a blank line will be inserted between dependencies of different configurations. Enabled by default.
 *   insertBlankLines = false
 *
 *   // Sort direct calls in matching Gradle DSL blocks
 *   blocks("sqldelight.databases.create")
 *
 *   // true by default, meaning that 'checkSortDependencies' is a dependency of 'check'
 *   check(true)
 * }
 */
abstract class SortDependenciesExtension @Inject constructor(
  objects: ObjectFactory,
) {

  /** Defines a custom version of the SortDependencies CLI to use. */
  val version: Property<String> = objects.property(String::class.java)
    .convention(
      javaClass.classLoader.getResourceAsStream(VERSION_FILENAME)
        ?.bufferedReader()
        ?.use(BufferedReader::readLine)
        ?: error("Can't find '$VERSION_FILENAME'")
    )

  /**
   * When true, a blank line will be inserted between dependencies of different configurations
   * (`api`, `implementation`, etc.). Enabled by default.
   */
  val insertBlankLines: Property<Boolean> = objects
    .property(Boolean::class.java)
    .convention(true)

  internal val blocks: SetProperty<String> = objects
    .setProperty(String::class.java)
    .convention(emptySet())

  /** Sorts direct calls in Gradle DSL blocks matching any of [paths]. */
  fun blocks(vararg paths: String) {
    blocks.addAll(*paths)
  }

  internal val check: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

  fun check(shouldCheck: Boolean) {
    check.set(shouldCheck)
    check.disallowChanges()
  }

  internal companion object {
    private const val VERSION_FILENAME = "com-squareup-sort-version.txt"

    fun create(project: Project): SortDependenciesExtension {
      return project.extensions.create("sortDependencies", SortDependenciesExtension::class.java)
    }
  }
}
