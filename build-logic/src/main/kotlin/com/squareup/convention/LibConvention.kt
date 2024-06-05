package com.squareup.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

/**
 * This plugin is applied to our JVM lib projects.
 * ```
 * plugins {
 *   id("com.squareup.lib")
 * }
 * ```
 */
class LibConvention : Plugin<Project> {

  override fun apply(target: Project): Unit = target.run {
    pluginManager.apply(BaseConvention::class.java)
  }
}
