package com.squareup.sort

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class SortDependenciesPlugin : Plugin<Project> {

  private companion object {
    const val VERSION_FILENAME = "version.txt"
  }

  override fun apply(target: Project): Unit = target.run {
    tasks.register("sortDependencies", SortDependenciesTask::class.java) { t ->
      with(t) {
        val version = javaClass.classLoader.getResourceAsStream(VERSION_FILENAME)
          ?.bufferedReader()
          ?.use { it.readLine() }
          ?: error("Can't find $VERSION_FILENAME")
        val coordinates = "com.squareup:sort-gradle-dependencies-app:$version"
        val app = configurations.detachedConfiguration(dependencies.create(coordinates))

        buildScript.set(buildFile)
        sortProgram.setFrom(app)
        mode.convention("sort")
      }
    }
  }
}