package com.squareup.sort

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option

abstract class BaseSortDependenciesTask : DefaultTask() {

  @get:Classpath
  abstract val sortProgram: ConfigurableFileCollection

  /** The app version limits what options we can pass it. */
  @get:Input
  abstract val version: Property<String>

  @get:Optional
  @get:Option(option = "verbose", description = "Enables verbose logging.")
  @get:Input
  abstract val verbose: Property<Boolean>

  @get:Optional
  @get:Input
  abstract val insertBlankLines: Property<Boolean>

  /** Dotted Gradle DSL block paths whose direct calls should be sorted. */
  @get:Input
  abstract val blocks: SetProperty<String>
}
