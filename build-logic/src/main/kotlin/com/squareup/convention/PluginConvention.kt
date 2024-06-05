package com.squareup.convention

import com.squareup.convention.publishing.Publishing
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile

/**
 * This plugin is applied to our gradle plugin projects.
 * ```
 * plugins {
 *   id("com.squareup.plugin")
 * }
 * ```
 */
@Suppress("unused")
class PluginConvention : Plugin<Project> {

  override fun apply(target: Project): Unit = target.run {
    with(pluginManager) {
      apply(LibConvention::class.java)
      apply("java-gradle-plugin")
    }

    tasks.withType(KotlinCompile::class.java).configureEach {
      it.kotlinOptions {
        // Gradle plugins require this
        freeCompilerArgs = freeCompilerArgs + listOf("-Xsam-conversions=class")
      }
    }

    Publishing.setup(this)
  }
}
