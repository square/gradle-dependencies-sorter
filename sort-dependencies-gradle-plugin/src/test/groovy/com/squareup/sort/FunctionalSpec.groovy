package com.squareup.sort

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static com.squareup.test.Runner.build
import static com.squareup.test.Runner.buildAndFail

final class FunctionalSpec extends Specification {

  private static final Path REPO = System.getProperty('rootDir').with {
    Paths.get(it, 'app/build/for-tests')
  }

  @TempDir
  Path dir

  def "can sort build.gradle"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, BUILD_SCRIPT)

    when: 'We sort dependencies'
    build(dir, 'sortDependencies')

    then: 'Dependencies are sorted'
    buildScript.text == """\
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
      }
    """.stripIndent()
  }

  def "can sort build.gradle.kts"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript, BUILD_SCRIPT_KTS)

    when: 'We sort dependencies'
    build(dir, 'sortDependencies')

    then: 'Dependencies are sorted'
    buildScript.text == """\
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
      }
    """.stripIndent()
  }

  def "can check sort order"() {
    given: 'A build script with unsorted dependencies'
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, BUILD_SCRIPT)

    when: 'We sort dependencies'
    def result = buildAndFail(dir, 'sortDependencies', '--mode', 'check')

    then: 'Dependencies are not sorted'
    result.output.contains('1 scripts are not ordered correctly.')
  }

  private static final BUILD_SCRIPT = """\
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
    }
  """.stripIndent()

  private static final BUILD_SCRIPT_KTS = """\
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
    }
  """.stripIndent()
}
