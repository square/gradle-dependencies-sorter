package com.squareup.sort

import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import com.squareup.sort.groovy.GroovySorter
import com.squareup.sort.kotlin.KotlinSorter
import spock.lang.PendingFeature
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static com.google.common.truth.Truth.assertThat

final class GroovySorterSpec extends Specification {

  @TempDir
  Path dir

  def "can sort build script"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          import foo
          import static bar;

          plugins {
            id 'foo'
          }

          repositories {
            google()
            mavenCentral()
          }

          apply plugin: 'bar'
          ext.magic = 42

          android {
            whatever
          }

          dependencies {
            implementation 'heart:of-gold:1.0'
            api project(":marvin")

            implementation 'b:1.0'
            implementation 'a:1.0'
            // Here's a multi-line comment
            // Here's the second line of the comment
            implementation deps.foo

            /*
             * Here's a multiline comment.
             */
            implementation deps.bar

            testImplementation("pan-galactic:gargle-blaster:2.0-SNAPSHOT") {
              because "life's too short not to"
            }

            implementation project(':milliways')
            api 'zzz:yyy:1.0'
          }

          println 'hello, world'
        '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    expect:
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          import foo
          import static bar;

          plugins {
            id 'foo'
          }

          repositories {
            google()
            mavenCentral()
          }

          apply plugin: 'bar'
          ext.magic = 42

          android {
            whatever
          }

          dependencies {
            api project(":marvin")
            api 'zzz:yyy:1.0'

            implementation project(':milliways')
            implementation 'a:1.0'
            implementation 'b:1.0'
            implementation 'heart:of-gold:1.0'
            /*
             * Here's a multiline comment.
             */
            implementation deps.bar
            // Here's a multi-line comment
            // Here's the second line of the comment
            implementation deps.foo

            testImplementation("pan-galactic:gargle-blaster:2.0-SNAPSHOT") {
              because "life's too short not to"
            }
          }

          println 'hello, world'
        '''.stripIndent()
    )).inOrder()
  }

  def "can sort build script with gradleApi() dep"() {
    given:
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript,
      '''\
          dependencies {
            implementation 'heart:of-gold:1.0'
            api project(':marvin')

            implementation 'sad:robot:1.0'
            api gradleApi()
            implementation testFixtures(libs.magic)
            implementation platform(project(':platform'))
            implementation enforcedPlatform(libs.bigBom)
          }'''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    expect:
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api project(':marvin')
            api gradleApi()

            implementation enforcedPlatform(libs.bigBom)
            implementation platform(project(':platform'))
            implementation testFixtures(libs.magic)
            implementation 'heart:of-gold:1.0'
            implementation 'sad:robot:1.0'
          }'''.stripIndent()
    )).inOrder()
  }

  def "can sort testFixtures correctly"() {
    given:
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript,
      '''\
        dependencies {
          testFixturesImplementation 'g:a:1'
          testFixturesApi 'g:b:1'
          implementation libs.c
          api libs.d
          testImplementation 'g:e:1'
        }'''.stripIndent()
    )
    def sorter = GroovySorter.of(buildScript)

    expect:
    assertThat(sorter.rewritten()).isEqualTo(
      '''\
        dependencies {
          api libs.d

          implementation libs.c

          testFixturesApi 'g:b:1'

          testFixturesImplementation 'g:a:1'

          testImplementation 'g:e:1'
        }'''.stripIndent()
    )
  }

  // GroovySorter may never support complex syntax in a dependencies block
  @PendingFeature
  def "doesn't remove complex statements when sorting"() {
    given:
    def buildScript = dir.resolve('build.gradle.kts')
    Files.writeString(buildScript,
      '''\
        dependencies {
          implementation(libs.c)
          api(libs.d)
          testImplementation("g:e:1")

          if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
            // Multi-line comment about why we're
            // doing this.
            testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
          }
        }'''.stripIndent()
    )
    def sorter = KotlinSorter.of(buildScript)

    expect:
    assertThat(sorter.rewritten()).isEqualTo(
      '''\
        dependencies {
          api(libs.d)

          implementation(libs.c)

          testImplementation("g:e:1")

          if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
            // Multi-line comment about why we're
            // doing this.
            testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
          }
        }'''.stripIndent()
    )
  }

  def "can sort build script with four-space tabs"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
              implementation 'heart:of-gold:1.0'
              api project(":marvin")

              implementation 'b:1.0'
              implementation 'a:1.0'
              // Here's a multi-line comment
              // Here's the second line of the comment
              implementation deps.foo

              /*
               * Here's a multiline comment.
               */
              implementation deps.bar

              testImplementation("pan-galactic:gargle-blaster:2.0-SNAPSHOT") {
                because "life's too short not to"
              }

              implementation project(':milliways')
              api 'zzz:yyy:1.0'
          }
        '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    expect:
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
              api project(":marvin")
              api 'zzz:yyy:1.0'

              implementation project(':milliways')
              implementation 'a:1.0'
              implementation 'b:1.0'
              implementation 'heart:of-gold:1.0'
              /*
               * Here's a multiline comment.
               */
              implementation deps.bar
              // Here's a multi-line comment
              // Here's the second line of the comment
              implementation deps.foo

              testImplementation("pan-galactic:gargle-blaster:2.0-SNAPSHOT") {
                because "life's too short not to"
              }
          }
        '''.stripIndent()
    )).inOrder()
  }

  def "colons have higher precedence than hyphen"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
            api project(":marvin-robot:so-sad")
            api project(":marvin:robot:so-sad")
          }
        '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    expect:
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api project(":marvin:robot:so-sad")
            api project(":marvin-robot:so-sad")
          }
        '''.stripIndent()
    )).inOrder()
  }

  def "single and double quotes are treated as equivalent"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
            api project(':a')
            api project(":b")
          }
        '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    when:
    sorter.rewritten()

    then:
    thrown(AlreadyOrderedException)
  }

  // We have observed that, given the start "dependencies{" (no space), and a project dependency, the
  // parser fails. For some reason this combination was confusing the lexer, which treated
  // "dependencies{" as if it matched the 'text' rule, rather than the 'dependencies' rule.
  def "can sort a dependencies{ block"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies{
            api project(':nu-metal')
            api project(':magic')
          }
        '''.stripIndent())

    when:
    def newScript = GroovySorter.of(buildScript).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api project(':magic')
            api project(':nu-metal')
          }
        '''.stripIndent()
    )).inOrder()
  }

  def "will not sort already sorted build script"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          import foo
          import static bar;

          plugins {
            id 'foo'
          }

          repositories {
            google()
            mavenCentral()
          }

          apply plugin: 'bar'
          ext.magic = 42

          android {
            whatever
          }

          dependencies {
            api project(":marvin")
            api 'zzz:yyy:1.0'

            implementation project(':milliways')
            implementation 'a:1.0'
            implementation 'b:1.0'
            implementation 'heart:of-gold:1.0'
            implementation deps.bar
            implementation deps.foo

            testImplementation("pan-galactic:gargle-blaster:2.0-SNAPSHOT") {
              because "life's too short not to"
            }
          }

          println 'hello, world'
        '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    when:
    sorter.rewritten()

    then:
    thrown(AlreadyOrderedException)
  }

  def "sort can handle 'path:' notation"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
        dependencies {
          api project(":path:path")
          api project(":zaphod")
          api project(path: ":beeblebrox", configuration: 'solipsism')
          api project(   path: ':path')

          api project( ":eddie" )
          api project(":eddie:eddie")
          api project(path: ":trillian")
        }
      '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    expect:
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
        dependencies {
          api project(path: ":beeblebrox", configuration: 'solipsism')
          api project( ":eddie" )
          api project(":eddie:eddie")
          api project(   path: ':path')
          api project(":path:path")
          api project(path: ":trillian")
          api project(":zaphod")
        }
      '''.stripIndent()
    )).inOrder()
  }

  def "a script without dependencies is already sorted"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          plugins {
            id 'foo'
          }
        '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    expect:
    sorter.isSorted()
  }

  def "a script with an empty dependencies is already sorted"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
          }
        '''.stripIndent())
    def sorter = GroovySorter.of(buildScript)

    expect:
    sorter.isSorted()
  }

  def "dedupe identical dependencies"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
            implementation(projects.foo)
            implementation(projects.bar)
            implementation(projects.foo)

            api(projects.foo)
            api(projects.bar)
            api(projects.foo)
          }
        '''.stripIndent())

    when:
    def newScript = GroovySorter.of(buildScript).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api(projects.bar)
            api(projects.foo)

            implementation(projects.bar)
            implementation(projects.foo)
          }
        '''.stripIndent()
    )).inOrder()
  }

  def "keep identical dependencies that have non-identical comments"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
            // Foo implementation
            implementation(projects.foo)
            implementation(projects.bar)
            // Foo implementation
            implementation(projects.foo)

            // Foo api 1st
            api(projects.foo)
            api(projects.bar)
            // Foo api 2nd
            api(projects.foo)
          }
        '''.stripIndent())

    when:
    def newScript = GroovySorter.of(buildScript).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api(projects.bar)
            // Foo api 1st
            api(projects.foo)
            // Foo api 2nd
            api(projects.foo)

            implementation(projects.bar)
            // Foo implementation
            implementation(projects.foo)
          }
        '''.stripIndent()
    )).inOrder()
  }

  def "sort without inserting newlines between different configurations"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
            implementation(projects.foo)
            implementation(projects.bar)
            implementation(projects.foo)

            api(projects.foo)
            api(projects.bar)
            api(projects.foo)
          }
        '''.stripIndent())

    when:
    def config = new Sorter.Config(false)
    def newScript = GroovySorter.of(buildScript, config).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api(projects.bar)
            api(projects.foo)
            implementation(projects.bar)
            implementation(projects.foo)
          }
        '''.stripIndent()
    )).inOrder()
  }

  def "sort add function call in dependencies"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
          dependencies {
            implementation(projects.foo)
            implementation(projects.bar)

            api(projects.foo)
            api(projects.bar)

            add("debugImplementation", projects.foo)
            add(releaseImplementation, projects.foo)
          }'''.stripIndent())

    when:
    def newScript = GroovySorter.of(buildScript).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    assertThat(newScript).isEqualTo(
      '''\
        dependencies {
          api(projects.bar)
          api(projects.foo)

          implementation(projects.bar)
          implementation(projects.foo)

          add("debugImplementation", projects.foo)
          add(releaseImplementation, projects.foo)
        }'''.stripIndent()
    )
  }

  def "can sort dependencies with artifact type specified"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      '''\
        dependencies {
          implementation projects.foo.internal
          implementation projects.bar.public
          implementation (libs.baz.ui) {
            artifact {
              type = "aar"
            }
          }
          implementation libs.androidx.constraintLayout
          implementation libs.common.view
          implementation projects.core
        }'''.stripIndent())
    when:
    def newScript = GroovySorter.of(buildScript).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    assertThat(newScript).isEqualTo(
      '''\
        dependencies {
          implementation libs.androidx.constraintLayout
          implementation (libs.baz.ui) {
            artifact {
              type = "aar"
            }
          }
          implementation libs.common.view
          implementation projects.bar.public
          implementation projects.core
          implementation projects.foo.internal
        }'''.stripIndent()
    )
  }

  // https://github.com/square/gradle-dependencies-sorter/issues/59
  def "can sort multiple semantically different dependencies blocks"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript,
      """\
      import app.cash.redwood.buildsupport.FlexboxHelpers

      apply plugin: 'com.android.library'
      apply plugin: 'org.jetbrains.kotlin.multiplatform'
      apply plugin: 'org.jetbrains.kotlin.plugin.serialization'
      apply plugin: 'app.cash.paparazzi'
      apply plugin: 'com.vanniktech.maven.publish'
      apply plugin: 'org.jetbrains.dokka' // Must be applied here for publish plugin.
      apply plugin: 'app.cash.redwood.build.compose'

      kotlin {
        android {
          publishLibraryVariants('release')
        }

        iosArm64()
        iosX64()
        iosSimulatorArm64()

        jvm()

        macosArm64()
        macosX64()

        sourceSets {
          commonMain {
            kotlin.srcDir(FlexboxHelpers.get(tasks, 'app.cash.redwood.layout.composeui').get())
            dependencies {
              api projects.redwoodLayoutWidget
              implementation projects.redwoodFlexbox
              implementation projects.redwoodWidgetCompose
              implementation libs.jetbrains.compose.foundation
            }
          }

          androidUnitTest {
            dependencies {
              implementation projects.redwoodLayoutSharedTest
            }
          }
        }
      }

      android {
        namespace 'app.cash.redwood.layout.composeui'
      }""".stripIndent()
    )

    when:
    def newScript = GroovySorter.of(buildScript).rewritten()

    then:
    assertThat(newScript).isEqualTo(
      """\
      import app.cash.redwood.buildsupport.FlexboxHelpers

      apply plugin: 'com.android.library'
      apply plugin: 'org.jetbrains.kotlin.multiplatform'
      apply plugin: 'org.jetbrains.kotlin.plugin.serialization'
      apply plugin: 'app.cash.paparazzi'
      apply plugin: 'com.vanniktech.maven.publish'
      apply plugin: 'org.jetbrains.dokka' // Must be applied here for publish plugin.
      apply plugin: 'app.cash.redwood.build.compose'

      kotlin {
        android {
          publishLibraryVariants('release')
        }

        iosArm64()
        iosX64()
        iosSimulatorArm64()

        jvm()

        macosArm64()
        macosX64()

        sourceSets {
          commonMain {
            kotlin.srcDir(FlexboxHelpers.get(tasks, 'app.cash.redwood.layout.composeui').get())
            dependencies {
              api projects.redwoodLayoutWidget

              implementation libs.jetbrains.compose.foundation
              implementation projects.redwoodFlexbox
              implementation projects.redwoodWidgetCompose
      }
          }

          androidUnitTest {
            dependencies {
              implementation projects.redwoodLayoutSharedTest
      }
          }
        }
      }

      android {
        namespace 'app.cash.redwood.layout.composeui'
      }""".stripIndent()
    )
  }

  private static List<String> trimmedLinesOf(CharSequence content) {
    // to lines and trim whitespace off end
    return content.readLines().collect { it.replaceFirst('\\s+\$', '') }
  }
}
