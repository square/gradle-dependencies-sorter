package com.squareup.sort

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class SortDependenciesPlugin : Plugin<Project> {

  companion object {
    private const val VERSION_FILENAME = "version.txt"
    const val CONFIGURATION_NAME = "sortDependencies"
  }

  override fun apply(target: Project): Unit = target.run {
    val version = SortDependenciesPlugin::class.java.classLoader.getResourceAsStream(VERSION_FILENAME)
      ?.bufferedReader()
      ?.use { it.readLine() }
      ?: error("Can't find $VERSION_FILENAME")
    val coordinates = "com.squareup:sort-gradle-dependencies-app:$version"
    val configuration = configurations.create(CONFIGURATION_NAME)
      .defaultDependencies {
        it.add(dependencies.create(coordinates))
      }

    tasks.register("sortDependencies", SortDependenciesTask::class.java) { t ->
      with(t) {
        buildScript.set(buildFile)
        sortProgram.setFrom(configuration)
        mode.convention("sort")
      }
    }
  }
}