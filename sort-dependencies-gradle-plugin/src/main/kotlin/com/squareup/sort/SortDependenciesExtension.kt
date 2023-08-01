package com.squareup.sort

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.io.BufferedReader
import javax.inject.Inject

abstract class SortDependenciesExtension @Inject constructor(
  objects: ObjectFactory
) {

  internal companion object {
    private const val VERSION_FILENAME = "com-squareup-sort-version.txt"

    fun create(project: Project): SortDependenciesExtension {
      return project.extensions.create("sortDependencies", SortDependenciesExtension::class.java)
    }
  }

  /** Defines a custom version of the SortDependencies CLI to use. */
  val version: Property<String> = objects.property(String::class.java)
    .convention(
      javaClass.classLoader.getResourceAsStream(VERSION_FILENAME)
        ?.bufferedReader()
        ?.use(BufferedReader::readLine)
        ?: error("Can't find '$VERSION_FILENAME'")
    )
}
