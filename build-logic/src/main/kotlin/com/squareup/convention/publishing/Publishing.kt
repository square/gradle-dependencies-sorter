package com.squareup.convention.publishing

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom

@Suppress("UnstableApiUsage")
internal object Publishing {

  fun setup(project: Project): Unit = project.run {
    project.pluginManager.withPlugin("com.vanniktech.maven.publish") {
      extensions.getByType(MavenPublishBaseExtension::class.java).pom(::setupPom)
    }
  }

  private fun setupPom(pom: MavenPom): Unit = pom.run {
    name.set("Gradle Dependencies Sorter")
    description.set("Sorts Gradle dependencies")

    developers {
      it.developer { d ->
        d.id.set("autonomousapps")
        d.name.set("Tony Robalik")
      }
      it.developer { d ->
        d.id.set("darshanparajuli")
        d.name.set("Darshan Parajuli")
      }
      it.developer { d ->
        d.id.set("holmes")
        d.name.set("Jason Holmes")
      }
    }
  }
}
