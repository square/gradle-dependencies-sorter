package com.squareup.convention

import com.squareup.convention.publishing.Publishing
import org.gradle.api.Plugin
import org.gradle.api.Project

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
      apply(BaseConvention::class.java)
      apply(LibConvention::class.java)
      apply("java-gradle-plugin")
    }

    Publishing.setup(this)
  }
}