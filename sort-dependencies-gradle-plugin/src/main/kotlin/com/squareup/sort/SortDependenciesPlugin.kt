package com.squareup.sort

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.language.base.plugins.LifecycleBasePlugin

@Suppress("unused")
class SortDependenciesPlugin : Plugin<Project> {

  private companion object {
    const val VERSION_FILENAME = "com-squareup-sort-version.txt"
  }

  private lateinit var sortApp: Configuration

  override fun apply(target: Project): Unit = target.run {
    sortApp = sortApp(this)

    tasks.register("sortDependencies", SortDependenciesTask::class.java) { t ->
      t.configure("sort", target)
    }
    val checkTask = tasks.register("checkSortDependencies", SortDependenciesTask::class.java) { t ->
      t.configure("check", target)
    }

    pluginManager.withPlugin("lifecycle-base") {
      tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
        it.dependsOn(checkTask)
      }
    }
  }

  private fun SortDependenciesTask.configure(mode: String, project: Project) {
    buildScript.set(project.buildFile)
    sortProgram.setFrom(sortApp)
    this.mode.set(mode)
  }

  private fun sortApp(project: Project): Configuration {
    val version = javaClass.classLoader.getResourceAsStream(VERSION_FILENAME)
      ?.bufferedReader()
      ?.use { it.readLine() }
      ?: error("Can't find $VERSION_FILENAME")
    val coordinates = "com.squareup:sort-gradle-dependencies-app:$version"

    return project.configurations.detachedConfiguration(project.dependencies.create(coordinates))
  }
}
