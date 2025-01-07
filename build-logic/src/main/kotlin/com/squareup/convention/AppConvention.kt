package com.squareup.convention

import com.squareup.convention.publishing.Publishing
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This plugin is applied to our JVM app projects.
 * ```
 * plugins {
 *   id("com.squareup.app")
 * }
 * ```
 */
@Suppress("unused")
class AppConvention : Plugin<Project> {

  override fun apply(target: Project): Unit = target.run {
    with(pluginManager) {
      apply(BaseConvention::class.java)
      apply("application")
      apply("com.gradleup.shadow")
    }

    Publishing.setup(this)
  }
}
