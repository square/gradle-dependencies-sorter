package com.squareup.sort

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin

@Suppress("unused")
class SortDependenciesPlugin : Plugin<Project> {

  private companion object {
    const val VERSION_FILENAME = "version.txt"
  }

  override fun apply(target: Project): Unit = target.run {
    tasks.register("sortDependencies", SortDependenciesTask::class.java) { t ->
      t.configure("sort", target)
    }
    val checkTask = tasks.register("checkSortDependencies", SortDependenciesTask::class.java) { t ->
      t.configure("check", target)
    }
    pluginManager.withPlugin("java-base") {
      target.project.tasks.named(JavaBasePlugin.CHECK_TASK_NAME, Task::class.java).configure {
        it.dependsOn(checkTask)
      }
    }
  }

  private fun SortDependenciesTask.configure(mode: String, project: Project) {
      val version = javaClass.classLoader.getResourceAsStream(VERSION_FILENAME)
        ?.bufferedReader()
        ?.use { it.readLine() }
        ?: error("Can't find $VERSION_FILENAME")
      val coordinates = "com.squareup:sort-gradle-dependencies-app:$version"
      val app = project.configurations.detachedConfiguration(project.dependencies.create(coordinates))

      buildScript.set(project.buildFile)
      sortProgram.setFrom(app)
      this.mode.set(mode)
  }
}
