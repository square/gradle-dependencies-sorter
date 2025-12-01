package com.squareup.sort

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ProjectLayout
import org.gradle.language.base.plugins.LifecycleBasePlugin

@Suppress("unused")
class SortDependenciesPlugin : Plugin<Project> {

  private companion object {
    const val VERSION_FILENAME = "com-squareup-sort-version.txt"
  }

  private lateinit var extension: SortDependenciesExtension

  override fun apply(target: Project): Unit = target.run {
    extension = SortDependenciesExtension.create(this)

    // nb: Can't use a detached configuration because that needs a Dependency, not a dependency notation. The latter can
    // be lazily evaluated (as below) while the former needs to (e.g.) know its version eagerly: it is more constrained.
    val sortApp = configurations.maybeCreate("sortDependencies").apply {
      target.dependencies.add(
        name,
        extension.version.map { v -> "com.squareup:sort-gradle-dependencies-app:$v" }
      )
    }

    tasks.register("sortDependencies", SortDependenciesTask::class.java) { t ->
      t.configure(target, sortApp, extension)
    }
    val checkTask = tasks.register("checkSortDependencies", CheckSortDependenciesTask::class.java) { t ->
      t.configure(target, sortApp, layout, extension)
    }

    afterEvaluate {
      val shouldCheck = extension.check.get()
      if (shouldCheck) {
        pluginManager.withPlugin("lifecycle-base") {
          tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
            it.dependsOn(checkTask)
          }
        }
      }
    }
  }

  private fun SortDependenciesTask.configure(
    project: Project,
    sortApp: Configuration,
    extension: SortDependenciesExtension,
  ) {
    buildScript.set(project.buildFile)
    sortProgram.setFrom(sortApp)
    version.set(extension.version)
    insertBlankLines.set(extension.insertBlankLines)
  }

  private fun CheckSortDependenciesTask.configure(
    project: Project,
    sortApp: Configuration,
    layout: ProjectLayout,
    extension: SortDependenciesExtension,
  ) {
    buildScript.set(project.buildFile)
    sortProgram.setFrom(sortApp)
    version.set(extension.version)
    output.set(layout.buildDirectory.file("reports/dependencies-sorter/report.txt"))
    insertBlankLines.set(extension.insertBlankLines)
  }
}
