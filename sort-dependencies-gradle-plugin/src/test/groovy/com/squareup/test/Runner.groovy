package com.squareup.test

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import java.lang.management.ManagementFactory
import java.nio.file.Path

final class Runner {

  private Runner() {
  }

  static BuildResult build(Path projectDir, String... args) {
    return runner(projectDir, args).build()
  }

  static BuildResult buildAndFail(Path projectDir, String... args) {
    return runner(projectDir, args).buildAndFail()
  }

  private static GradleRunner runner(Path projectDir, String... args) {
    return GradleRunner.create()
      .forwardOutput()
      .withPluginClasspath()
      .withProjectDir(projectDir.toFile())
      .withArguments(*args, '-s')
      // evaluates to true when running tests in debug mode
      .withDebug(ManagementFactory.getRuntimeMXBean().inputArguments.toString().indexOf('-agentlib:jdwp') > 0)
  }
}
