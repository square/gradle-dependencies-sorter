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
import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.google.common.truth.Truth.assertThat

final class GroovySorterSpec extends Specification {

  @TempDir
  Path dir

  def "can sort build script"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
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
            api projects.foo
            implementation projects.foo.internal
            api projects.bar
            implementation projects.bar.internal

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
        ''', lineSeparator)
    Files.writeString(buildScript, fileContent)
    def config = new Sorter.Config(true)
    def sorter = GroovySorter.of(buildScript, config, lineSeparator)

    expect:
    extractLineSeparators(sorter.rewritten()).every { it == lineSeparator }
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
            api projects.bar
            api projects.foo
            api 'zzz:yyy:1.0'

            implementation project(':milliways')
            implementation projects.bar.internal
            implementation projects.foo.internal
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

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "can sort build script with gradleApi() dep"() {
    given:
    def buildScript = dir.resolve('build.gradle.kts')
    def fileContent = normalize(
      '''\
          dependencies {
            implementation 'heart:of-gold:1.0'
            api project(':marvin')

            implementation 'sad:robot:1.0'
            api gradleApi()
            implementation testFixtures(libs.magic)
            implementation platform(project(':platform'))
            implementation enforcedPlatform(libs.bigBom)
          }''',
      lineSeparator
    )
    Files.writeString(buildScript, fileContent)
    def config = new Sorter.Config(true)
    def sorter = GroovySorter.of(buildScript, config, lineSeparator)

    expect:
    extractLineSeparators(sorter.rewritten()).every { it == lineSeparator }
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

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "can sort testFixtures correctly"() {
    given:
    def buildScript = dir.resolve('build.gradle.kts')
    def fileContent = normalize(
      '''\
        dependencies {
          testFixturesImplementation 'g:a:1'
          testFixturesApi 'g:b:1'
          implementation libs.c
          api libs.d
          testImplementation 'g:e:1'
        }''',
        lineSeparator
    )
    Files.writeString(buildScript, fileContent)
    def config = new Sorter.Config(true)
    def sorter = GroovySorter.of(buildScript, config, lineSeparator)

    expect:
    extractLineSeparators(sorter.rewritten()).every { it == lineSeparator }
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
        dependencies {
          api libs.d

          implementation libs.c

          testFixturesApi 'g:b:1'

          testFixturesImplementation 'g:a:1'

          testImplementation 'g:e:1'
        }'''.stripIndent()
    )).inOrder()

    where:
    lineSeparator << ['\n', '\r\n']
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
    def fileContent = normalize('''\
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
        ''', lineSeparator)
    Files.writeString(buildScript, fileContent)
    def config = new Sorter.Config(true)
    def sorter = GroovySorter.of(buildScript, config, lineSeparator)

    expect:
    extractLineSeparators(sorter.rewritten()).every { it == lineSeparator }
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

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "project path separators sort after hyphens without changing external coordinates"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
          dependencies {
            api project(":feature:foo:shared")
            api project(":feature:foo-v2")
            api project(":feature:foo")
            api project("relative:foo:shared")
            api project("relative:foo-v2")
            api ":foo-bar:baz"
            api "foo-bar:baz"
            api ":foo:bar-baz"
            api "foo:bar:baz"
          }
        ''', lineSeparator)
    Files.writeString(buildScript, fileContent)
    def config = new Sorter.Config(true)
    def sorter = GroovySorter.of(buildScript, config, lineSeparator)

    expect:
    extractLineSeparators(sorter.rewritten()).every { it == lineSeparator }
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api project(":feature:foo")
            api project(":feature:foo-v2")
            api project(":feature:foo:shared")
            api project("relative:foo-v2")
            api project("relative:foo:shared")
            api ":foo:bar-baz"
            api ":foo-bar:baz"
            api "foo:bar:baz"
            api "foo-bar:baz"
          }
        '''.stripIndent()
    )).inOrder()

    where:
    lineSeparator << ['\n', '\r\n']
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
    def fileContent = normalize('''\
          dependencies{
            api project(':nu-metal')
            api project(':magic')
          }
        ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    extractLineSeparators(newScript).every { it == lineSeparator }
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
          dependencies {
            api project(':magic')
            api project(':nu-metal')
          }
        '''.stripIndent()
    )).inOrder()

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "will not sort already sorted build script"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, '''\
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
    def fileContent = normalize('''\
        dependencies {
          api project(":path:path")
          api project(":zaphod")
          api project(path: ":beeblebrox", configuration: 'solipsism')
          api project(   path: ':path')

          api project( ":eddie" )
          api project(":eddie:eddie")
          api project(path: ":trillian")
        }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)
    def config = new Sorter.Config(true)
    def sorter = GroovySorter.of(buildScript, config, lineSeparator)

    expect:
    extractLineSeparators(sorter.rewritten()).every { it == lineSeparator }
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

    where:
    lineSeparator << ['\n', '\r\n']
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
    def fileContent = normalize('''\
          dependencies {
            implementation(projects.foo)
            implementation(projects.bar)
            implementation(projects.foo)

            api(projects.foo)
            api(projects.bar)
            api(projects.foo)
          }
        ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    extractLineSeparators(newScript).every { it == lineSeparator }
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

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "keep identical dependencies that have non-identical comments"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
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
        ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    extractLineSeparators(newScript).every { it == lineSeparator }
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

    where:
    lineSeparator << ['\n', '\r\n']
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
    def fileContent = normalize('''\
          dependencies {
            implementation(projects.foo)
            implementation(projects.bar)

            api(projects.foo)
            api(projects.bar)

            add("debugImplementation", projects.foo)
            add(releaseImplementation, projects.foo)
          }''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    extractLineSeparators(newScript).every { it == lineSeparator }
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
        dependencies {
          api(projects.bar)
          api(projects.foo)

          implementation(projects.bar)
          implementation(projects.foo)

          add("debugImplementation", projects.foo)
          add(releaseImplementation, projects.foo)
        }'''.stripIndent()
    )).inOrder()

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "can sort dependencies with artifact type specified"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
        dependencies {
          implementation projects.foo.internal
          implementation project(":marvin")
          implementation projects.bar.public
          implementation (libs.baz.ui) {
            artifact {
              type = "aar"
            }
          }
          implementation libs.androidx.constraintLayout
          implementation libs.common.view
          implementation projects.core
        }''', lineSeparator)
    Files.writeString(buildScript, fileContent)
    when:
    def config = new Sorter.Config(true)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    notThrown(BuildScriptParseException)

    and:
    extractLineSeparators(newScript).every { it == lineSeparator }
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
        dependencies {
          implementation project(":marvin")
          implementation projects.bar.public
          implementation projects.core
          implementation projects.foo.internal
          implementation libs.androidx.constraintLayout
          implementation (libs.baz.ui) {
            artifact {
              type = "aar"
            }
          }
          implementation libs.common.view
        }'''.stripIndent()
    )).inOrder()

    where:
    lineSeparator << ['\n', '\r\n']
  }

  // https://github.com/square/gradle-dependencies-sorter/issues/95
  def "sorts Gradle dependency constraints"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      dependencies {
        constraints {
          runtime 'g:c:1'
          // Required by the Gradle plugin.
          api("g:b:1") {
            because "The plugin loads this dependency directly"
          }
          api 'g:a:1'
        }

        implementation libs.z
        implementation libs.a
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def newScript = GroovySorter.of(buildScript, new Sorter.Config(true), lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      dependencies {
        constraints {
          api 'g:a:1'
          // Required by the Gradle plugin.
          api("g:b:1") {
            because "The plugin loads this dependency directly"
          }
          runtime 'g:c:1'
        }

        implementation libs.a
        implementation libs.z
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "sorts constraints when the containing dependencies are already ordered"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      dependencies {
        implementation libs.a
        implementation libs.z

        constraints {
          runtime 'g:b:1'
          api 'g:a:1'
        }
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def newScript = GroovySorter.of(buildScript, new Sorter.Config(true), lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      dependencies {
        implementation libs.a
        implementation libs.z

        constraints {
          api 'g:a:1'
          runtime 'g:b:1'
        }
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "matches dotted and nested custom block paths"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      android.sourceSets {
        zeta()
        alpha()
      }

      android {
        sourceSets {
          omega()
          beta()
        }
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['android.sourceSets'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      android.sourceSets {
        alpha()
        zeta()
      }

      android {
        sourceSets {
          beta()
          omega()
        }
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "sorts a qualified callable custom block"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      subprojects {
        sqldelight {
          databases {
            create("Database") {
              zeta() // Keep this with zeta.
              // Keep this with beta.
              beta {
                enabled = true
              }
              alpha()

              def marker = "fixed"

              delta()
              charlie()
            }
          }
        }

        other {
          create("unrelated") {
            zeta()
            alpha()
          }
        }
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['sqldelight.databases.create'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      subprojects {
        sqldelight {
          databases {
            create("Database") {
              alpha()
              // Keep this with beta.
              beta {
                enabled = true
              }
              zeta() // Keep this with zeta.

              def marker = "fixed"

              charlie()
              delta()
            }
          }
        }

        other {
          create("unrelated") {
            zeta()
            alpha()
          }
        }
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "keeps non-call syntax in place inside a custom block"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      custom { name, value ->
        zeta()
        omega()

        alpha()
          .because("why")

        delta()
        String marker
        charlie()

        zulu() /* Keep this comment
          with zulu. */
        beta()
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['custom'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      custom { name, value ->
        omega()
        zeta()

        alpha()
          .because("why")

        delta()
        String marker
        beta()

        charlie()
        zulu() /* Keep this comment
          with zulu. */
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "sorts command calls with bare arguments"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      custom {
        zeta value
        enabled true
        alpha value

        String marker
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['custom'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      custom {
        alpha value
        enabled true
        zeta value

        String marker
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "keeps control flow and typed declarations in place"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      custom {
        zeta()
        if (enabled) {
          marker()
        }
        omega()
        alpha()

        delta()
        String[] values
        charlie()
        beta()

        echo()
        Map<String, String> mapping
        gamma()
        foxtrot()
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['custom'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      custom {
        zeta()
        if (enabled) {
          marker()
        }
        alpha()
        delta()

        omega()
        String[] values
        beta()
        charlie()

        echo()
        Map<String, String> mapping
        foxtrot()
        gamma()
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "does not inherit block paths through anonymous closures"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      outer {
        def action = {
          target {
            zeta()
            alpha()
          }
        }

        target {
          zeta()
          alpha()
        }
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['outer.target'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      outer {
        def action = {
          target {
            zeta()
            alpha()
          }
        }

        target {
          alpha()
          zeta()
        }
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "ignores configured blocks inside slashy strings"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      def fixture() {
        return /(?s)
        custom {
          zeta()
          alpha()
        }
        /
      }

      def commandFixture() {
        println /(?s)
        custom {
          zeta()
          alpha()
        }
        /
      }

      custom {
        zeta()
        alpha()
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['custom'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      def fixture() {
        return /(?s)
        custom {
          zeta()
          alpha()
        }
        /
      }

      def commandFixture() {
        println /(?s)
        custom {
          zeta()
          alpha()
        }
        /
      }

      custom {
        alpha()
        zeta()
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "does not treat division operators as a slashy string"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      foo / bar

      custom {
        zeta()
        alpha()
      }

      baz / qux
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['custom'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      foo / bar

      custom {
        alpha()
        zeta()
      }

      baz / qux
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "ignores configured blocks inside strings"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      def regex = value =~ /(?s)
      sqldelight {
        databases {
          create("regex") {
            zeta()
            alpha()
          }
        }
      }
      /

      def dollarSlashy = $/
      escaped close /$$
      sqldelight {
        databases {
          create("dollar slashy") {
            zeta()
            alpha()
          }
        }
      }
      /$

      def buildscriptFixture = """
      buildscript {
      """

      def fixture = """
      sqldelight {
        databases {
          create("fixture") {
            zeta()
            alpha()
          }
        }
      }
      """

      sqldelight {
        databases {
          create("Database") {
            zeta()
            alpha()
          }
        }
      }

      def closingFixture = """
      }
      """
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['sqldelight.databases.create'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      def regex = value =~ /(?s)
      sqldelight {
        databases {
          create("regex") {
            zeta()
            alpha()
          }
        }
      }
      /

      def dollarSlashy = $/
      escaped close /$$
      sqldelight {
        databases {
          create("dollar slashy") {
            zeta()
            alpha()
          }
        }
      }
      /$

      def buildscriptFixture = """
      buildscript {
      """

      def fixture = """
      sqldelight {
        databases {
          create("fixture") {
            zeta()
            alpha()
          }
        }
      }
      """

      sqldelight {
        databases {
          create("Database") {
            alpha()
            zeta()
          }
        }
      }

      def closingFixture = """
      }
      """
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "sorts a configured block inside buildscript"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      buildscript {
        repositories {
          zeta()
          alpha()
        }
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true, ['buildscript.repositories'] as Set)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    newScript == normalize('''\
      buildscript {
        repositories {
          alpha()
          zeta()
        }
      }
      ''', lineSeparator)

    where:
    lineSeparator << ['\n', '\r\n']
  }

  // https://github.com/square/gradle-dependencies-sorter/issues/59
  def "can sort multiple semantically different dependencies blocks"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize("""\
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
              implementation libs.jetbrains.compose.foundation
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
      }""", lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    extractLineSeparators(newScript).every { it == lineSeparator }
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
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
    )).inOrder()

    where:
    lineSeparator << ['\n', '\r\n']
  }

  def "preserves a qualified nested dependencies block name"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    def fileContent = normalize('''\
      kotlin {
        sourceSets.commonMain.dependencies {
          implementation libs.z
          implementation libs.a
        }
      }
      ''', lineSeparator)
    Files.writeString(buildScript, fileContent)

    when:
    def config = new Sorter.Config(true)
    def newScript = GroovySorter.of(buildScript, config, lineSeparator).rewritten()

    then:
    extractLineSeparators(newScript).every { it == lineSeparator }
    assertThat(trimmedLinesOf(newScript)).containsExactlyElementsIn(trimmedLinesOf(
      '''\
      kotlin {
        sourceSets.commonMain.dependencies {
          implementation libs.a
          implementation libs.z
        }
      }
      '''.stripIndent()
    )).inOrder()

    where:
    lineSeparator << ['\n', '\r\n']
  }

  private static List<String> trimmedLinesOf(CharSequence content) {
    // to lines and trim whitespace off end
    return content.readLines().collect { it.replaceFirst('\\s+\$', '') }
  }

  private static CharSequence normalize(CharSequence input, String lineSeparator) {
    return input.stripIndent().replace('\n', lineSeparator)
  }

  private static List<String> extractLineSeparators(CharSequence input) {
    List<String> lineSeparators = new ArrayList<>()
    Matcher matcher = Pattern.compile("(\\r\\n|\\r|\\n)").matcher(input)
    while (matcher.find()) {
      lineSeparators.add(matcher.group())
    }
    return lineSeparators
  }
}
