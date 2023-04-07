package com.squareup.sort

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import java.io.BufferedReader
import javax.inject.Inject

abstract class SortDependenciesExtension @Inject constructor(
  objects: ObjectFactory,
  providers: ProviderFactory,
) {
  private companion object {
    private const val VERSION_FILENAME = "sortDependenciesVersion.txt"
  }

  /** Defines a custom version of the SortDependencies CLI to use. */
  val version: Property<String> = objects.property(String::class.java)
    .convention(providers.provider {
      SortDependenciesPlugin::class.java.classLoader.getResourceAsStream(VERSION_FILENAME)
        ?.bufferedReader()
        ?.use(BufferedReader::readLine)
        ?: error("Can't find $VERSION_FILENAME")
    })
}
