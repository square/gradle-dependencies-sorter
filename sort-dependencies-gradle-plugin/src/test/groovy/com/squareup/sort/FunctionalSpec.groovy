package com.squareup.sort

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

import static com.squareup.test.Runner.build
import static com.squareup.test.Runner.buildAndFail

final class FunctionalSpec extends Specification {

  private static final String REPO = System.getProperty('rootDir').with {
    Paths.get(it, 'app/build/for-tests').toString().replace('\\', '\\\\')
  }

  @TempDir
  Path dir

  def "can configure program version"() {
    given: 'a build script that sets program version explicitly'
    // nb: we can't easily use an earlier version because 0.2 doesn't support --verbose. The plugin code
    // would have to be modified to be version/feature-aware.
    def version = '0.3'
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, buildScriptWithVersion(version))

    when: 'We sort dependencies'
    build(dir, 'sortDependencies')

    then: 'Dependencies are sorted'
    buildScript.text == """\
      plugins {
        id 'java-library'
        id 'com.squareup.sort-dependencies'
      }

      sortDependencies {
        version = '$version'
      }

      repositories {
        mavenCentral()
        maven { url '$REPO' }
      }

      dependencies {
        implementation(platform('com.squareup.okhttp3:okhttp-bom:4.10.0'))
        implementation('com.squareup.okhttp3:okhttp:4.10.0')
        implementation('com.squareup.okio:okio:3.2.0')
      }""".stripIndent()
  }

  def "can sort build.gradle"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, BUILD_SCRIPT)

    when: 'We sort dependencies'
    build(dir, 'sortDependencies', '--verbose')

    then: 'Dependencies are sorted'
    buildScript.text == normalize("""\
      plugins {
        id 'java-library'
        id 'com.squareup.sort-dependencies'
      }

      repositories {
        mavenCentral()
        maven { url '$REPO' }
      }

      dependencies {
        implementation(platform('com.squareup.okhttp3:okhttp-bom:4.10.0'))
        implementation('com.squareup.okhttp3:okhttp:4.10.0')
        implementation('com.squareup.okio:okio:3.2.0')
      }""").stripIndent()
  }

  def "can sort build.gradle.kts"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript, BUILD_SCRIPT_KTS)

    when: 'We sort dependencies'
    build(dir, 'sortDependencies')

    then: 'Dependencies are sorted'
    buildScript.text == normalize("""\
      plugins {
        `java-library`
        id("com.squareup.sort-dependencies")
      }

      repositories {
        mavenCentral()
        maven { url = uri("$REPO") }
      }

      dependencies {
        implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
        implementation("com.squareup.okhttp3:okhttp:4.10.0")
        implementation("com.squareup.okio:okio:3.2.0")
      }""").stripIndent()
  }

  def "can sort build.gradle.kts with string property declaration"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript, BUILD_SCRIPT_KTS_STRING)

    when: 'We sort dependencies'
    build(dir, 'sortDependencies')

    then: 'Dependencies are sorted'
    buildScript.text == normalize("""\
      plugins {
        `java-library`
        id("com.squareup.sort-dependencies")
      }

      repositories {
        mavenCentral()
        maven { url = uri("$REPO") }
      }

      val okhttp3 = "com.squareup.okhttp3:okhttp:4.10.0"
      val okhttp3Bom = "com.squareup.okhttp3:okhttp-bom:4.10.0"

      dependencies {
        implementation(platform(okhttp3Bom))
        implementation("com.squareup.okio:okio:3.2.0")
        implementation(okhttp3)
      }""").stripIndent()
  }

  def "can check sort order"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, BUILD_SCRIPT)

    when: 'We check dependencies are sorted'
    def result = buildAndFail(dir, 'checkSortDependencies', '--verbose')

    then: 'Dependencies are not sorted'
    result.output.contains('1 scripts are not ordered correctly.')
    result.output.contains(
      '''\
      Fix by running
      ./gradlew sortDependencies'''.stripIndent()
    )
  }

  def "checks sort order when executing 'check' task"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, BUILD_SCRIPT)

    when: 'We run check task'
    def result = buildAndFail(dir, 'check')

    then: 'Task ran and failed'
    result.task(':checkSortDependencies').outcome == TaskOutcome.FAILED
  }

  def "doesn't check sort order when executing 'check' task when check is disabled"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, BUILD_SCRIPT)
    Files.writeString(buildScript,
      '''
        sortDependencies {
          check false
        }'''.stripIndent(),
      StandardOpenOption.APPEND
    )

    when: 'We run check task'
    def result = build(dir, 'check')

    then: 'Task did not run'
    result.task(':checkSortDependencies') == null
  }

  def "doesn't fail on empty build script"() {
    given: 'A project with an empty subproject build script'
    def settingsScript = dir.resolve('settings.gradle.kts')
    Files.writeString(settingsScript,
      """\
        dependencyResolutionManagement {
          repositories {
            mavenCentral()
            maven { url = uri("$REPO") }
          }
        }

        include(":sub")""".stripIndent()
    )
    def rootBuildScript = dir.resolve('build.gradle.kts')
    Files.writeString(rootBuildScript,
      '''\
      plugins {
        `java-library`
        id("com.squareup.sort-dependencies")
      }

      subprojects {
        apply(plugin = "com.squareup.sort-dependencies")
      }'''.stripIndent()
    )
    def subproject = dir.resolve('sub/build.gradle.kts')
    Files.createDirectories(subproject.parent)
    Files.createFile(subproject)

    expect: 'We sort dependencies'
    build(dir, 'sortDependencies', '--verbose')
  }

  def "doesn't fail on non-existent build script"() {
    given: 'A project with an intermediate directory that is technically a project'
    def settingsScript = dir.resolve('settings.gradle.kts')
    Files.writeString(settingsScript,
      """\
        dependencyResolutionManagement {
          repositories {
            mavenCentral()
            maven { url = uri("$REPO") }
          }
        }

        include(":sub:project")""".stripIndent()
    )
    def rootBuildScript = dir.resolve('build.gradle.kts')
    Files.writeString(rootBuildScript,
      '''\
      plugins {
        `java-library`
        id("com.squareup.sort-dependencies")
      }

      subprojects {
        apply(plugin = "com.squareup.sort-dependencies")
      }'''.stripIndent()
    )
    def subproject = dir.resolve('sub/project/build.gradle.kts')
    Files.createDirectories(subproject.parent)
    Files.createFile(subproject)

    expect: 'We sort dependencies'
    build(dir, 'sortDependencies', '--verbose')
  }

  def "no blank lines between different configurations when flag is disabled"() {
    given: 'A build script with unsorted dependencies and multiple configurations'
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript,
      normalize("""\
        plugins {
          `java-library`
          id("com.squareup.sort-dependencies")
        }

        sortDependencies {
          insertBlankLines = false
        }

        repositories {
          mavenCentral()
          maven { url = uri("$REPO") }
        }

        dependencies {
          implementation("com.squareup.okio:okio:3.2.0")
          api("com.squareup.okhttp3:okhttp:4.10.0")
          testImplementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
        }
      """).stripIndent()
    )

    when: 'We sort dependencies in the build folder'
    build(dir, 'sortDependencies')

    then: 'The build script is sorted with no blank lines between api and implementation'
    buildScript.text == normalize("""\
      plugins {
        `java-library`
        id("com.squareup.sort-dependencies")
      }

      sortDependencies {
        insertBlankLines = false
      }

      repositories {
        mavenCentral()
        maven { url = uri("$REPO") }
      }

      dependencies {
        api("com.squareup.okhttp3:okhttp:4.10.0")
        implementation("com.squareup.okio:okio:3.2.0")
        testImplementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
      }
    """).stripIndent()
  }

  def "insert blank lines between different configurations when flag is enabled"() {
    given: 'A build script with unsorted dependencies and multiple configurations'
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript,
      normalize("""\
        plugins {
          `java-library`
          id("com.squareup.sort-dependencies")
        }

        sortDependencies {
          insertBlankLines = true
        }

        repositories {
          mavenCentral()
          maven { url = uri("$REPO") }
        }

        dependencies {
          implementation("com.squareup.okio:okio:3.2.0")
          api("com.squareup.okhttp3:okhttp:4.10.0")
          testImplementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
        }
      """).stripIndent()
    )

    when: 'We sort dependencies in the build folder'
    build(dir, 'sortDependencies')

    then: 'The build script is sorted with a blank line between api and implementation'
    buildScript.text == normalize("""\
      plugins {
        `java-library`
        id("com.squareup.sort-dependencies")
      }

      sortDependencies {
        insertBlankLines = true
      }

      repositories {
        mavenCentral()
        maven { url = uri("$REPO") }
      }

      dependencies {
        api("com.squareup.okhttp3:okhttp:4.10.0")

        implementation("com.squareup.okio:okio:3.2.0")

        testImplementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
      }
    """).stripIndent()
  }

  private static final BUILD_SCRIPT = normalize("""\
    plugins {
      id 'java-library'
      id 'com.squareup.sort-dependencies'
    }

    repositories {
      mavenCentral()
      maven { url '$REPO' }
    }

    dependencies {
      implementation('com.squareup.okio:okio:3.2.0')
      implementation('com.squareup.okhttp3:okhttp:4.10.0')
      implementation(platform('com.squareup.okhttp3:okhttp-bom:4.10.0'))
    }""").stripIndent()

  private String buildScriptWithVersion(String version) {
    """\
      plugins {
        id 'java-library'
        id 'com.squareup.sort-dependencies'
      }

      sortDependencies {
        version = '$version'
      }

      repositories {
        mavenCentral()
        maven { url '$REPO' }
      }

      dependencies {
        implementation('com.squareup.okio:okio:3.2.0')
        implementation('com.squareup.okhttp3:okhttp:4.10.0')
        implementation(platform('com.squareup.okhttp3:okhttp-bom:4.10.0'))
      }""".stripIndent()
  }

  private static final BUILD_SCRIPT_KTS = normalize("""\
    plugins {
      `java-library`
      id("com.squareup.sort-dependencies")
    }

    repositories {
      mavenCentral()
      maven { url = uri("$REPO") }
    }

    dependencies {
      implementation("com.squareup.okio:okio:3.2.0")
      implementation("com.squareup.okhttp3:okhttp:4.10.0")
      implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
    }""").stripIndent()

  private static final BUILD_SCRIPT_KTS_STRING = normalize("""\
    plugins {
      `java-library`
      id("com.squareup.sort-dependencies")
    }

    repositories {
      mavenCentral()
      maven { url = uri("$REPO") }
    }

    val okhttp3 = "com.squareup.okhttp3:okhttp:4.10.0"
    val okhttp3Bom = "com.squareup.okhttp3:okhttp-bom:4.10.0"

    dependencies {
      implementation("com.squareup.okio:okio:3.2.0")
      implementation(okhttp3)
      implementation(platform(okhttp3Bom))
    }""").stripIndent()

    private static CharSequence normalize(CharSequence input, String lineSeparator = System.lineSeparator()) {
      return input.stripIndent().replace('\n', lineSeparator)
    }
}
