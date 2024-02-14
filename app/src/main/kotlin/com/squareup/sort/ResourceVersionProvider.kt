package com.squareup.sort

import picocli.CommandLine.IVersionProvider
import java.util.Properties

class ResourceVersionProvider : IVersionProvider {
  override fun getVersion(): Array<String> {
    return arrayOf(getVersionFromResource())
  }

  private fun getVersionFromResource(): String {
    val url = javaClass.getResource("/appinfo.properties")
    val properties = Properties()
    return if (url != null) {
      properties.load(url.openStream())
      properties.getProperty("app.version")
    } else {
      "No appinfo.properties file found in the classpath."
    }
  }
}
