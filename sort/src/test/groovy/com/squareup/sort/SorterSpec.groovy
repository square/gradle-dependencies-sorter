package com.squareup.sort

import com.squareup.parse.AlreadyOrderedException
import com.squareup.parse.BuildScriptParseException
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static com.google.common.truth.Truth.assertThat

final class SorterSpec extends Specification {

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
    def sorter = Sorter.sorterFor(buildScript)

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
    def sorter = Sorter.sorterFor(buildScript)

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
    def sorter = Sorter.sorterFor(buildScript)

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
    def sorter = Sorter.sorterFor(buildScript)

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
    def newScript = Sorter.sorterFor(buildScript).rewritten()

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
    def sorter = Sorter.sorterFor(buildScript)

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
          api project(path: ":beeblebrox")
          api project(   path: ':path')

          api project( ":eddie" )
          api project(":eddie:eddie")
          api project(path: ":trillian")
        }
      '''.stripIndent())
    def sorter = Sorter.sorterFor(buildScript)

    expect:
    assertThat(trimmedLinesOf(sorter.rewritten())).containsExactlyElementsIn(trimmedLinesOf(
      '''\
        dependencies {
          api project(path: ":beeblebrox")
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
    def sorter = Sorter.sorterFor(buildScript)

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
    def sorter = Sorter.sorterFor(buildScript)

    expect:
    sorter.isSorted()
  }

  private static List<String> trimmedLinesOf(CharSequence content) {
    // to lines and trim whitespace off end
    return content.readLines().collect { it.replaceFirst('\\s+\$', '') }
  }
}
