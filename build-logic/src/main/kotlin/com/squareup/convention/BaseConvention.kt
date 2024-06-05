package com.squareup.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

class BaseConvention : Plugin<Project> {

  override fun apply(target: Project): Unit = target.run {
    with(pluginManager) {
      apply("org.jetbrains.kotlin.jvm")
      apply("groovy")
    }

    // These are set in the base project's gradle.properties
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()

    val versionCatalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    val javaVersion = versionCatalog.findVersion("java").orElseThrow().requiredVersion
    extensions.configure(JavaPluginExtension::class.java) { j ->
      j.toolchain {
        it.languageVersion.set(JavaLanguageVersion.of(javaVersion))
      }
    }

    tasks.withType(GroovyCompile::class.java).configureEach {
      it.options.isIncremental = true
    }

    tasks.withType(Test::class.java).configureEach {
      it.useJUnitPlatform()
    }

    // Exclude Junit4. We really want to enforce Junit5 and unfortunately, Truth has a transitive dependency on 4.
    configurations.all {
      it.exclude(mapOf("group" to "junit", "module" to "junit"))
      it.exclude(mapOf("group" to "org.junit.vintage", "module" to "junit-vintage-engine"))
    }

    with(dependencies) {
      // Align all Kotlin components on the same version
      add("implementation", platform("org.jetbrains.kotlin:kotlin-bom"))

      // JUnit5 / Jupiter Platform stuff
      add("testImplementation", versionCatalog.findLibrary("junit-api").orElseThrow())
      add("testRuntimeOnly", versionCatalog.findLibrary("junit-engine").orElseThrow())

      add("testImplementation", versionCatalog.findLibrary("truth").orElseThrow())
    }
  }
}
