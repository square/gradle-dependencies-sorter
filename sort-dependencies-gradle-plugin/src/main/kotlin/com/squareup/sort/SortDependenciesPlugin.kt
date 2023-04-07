package com.squareup.sort

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class SortDependenciesPlugin : Plugin<Project> {

  companion object {
    const val CONFIGURATION_NAME = "sortDependencies"
  }

  override fun apply(target: Project): Unit = target.run {
    val extension = extensions.create("sortDependencies", SortDependenciesExtension::class.java)

    val app =
      configurations.detachedConfiguration(dependencies.create(extension.version.map { version -> "com.squareup:sort-gradle-dependencies-app:$version" }))

    tasks.register("sortDependencies", SortDependenciesTask::class.java) { t ->
      with(t) {
        buildScript.set(buildFile)
        sortProgram.setFrom(app)
        mode.convention("sort")
      }
    }
  }
}
