package com.squareup

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.squareup.sort.BuildDotGradleFinder
import com.squareup.sort.Mode
import com.squareup.sort.SortCommand
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Very basic spec as a toehold if we want to add more robust tests at the app level.
 *
 * @see <a href="https://ajalt.github.io/clikt/testing/">Clikt testing.</a>
 */
final class SortCommandSpec extends Specification {

  @TempDir
  Path dir

  def "can parse args"() {
    given:
    def buildScript = dir.resolve('build.gradle')
    Files.writeString(buildScript, """\
      plugins {
        id 'java-library'
        id 'com.squareup.sort-dependencies'
      }

      dependencies {
        implementation('com.squareup.okhttp3:okhttp:4.10.0')
        implementation('com.squareup.okio:okio:3.2.0')
      }
    """.stripIndent())
    def sortCommand = newSortCommand()

    when:
    int statusCode = runSortCommand(sortCommand, '-m', 'check', dir.toString())

    then:
    sortCommand.mode == Mode.CHECK
    sortCommand.paths == [dir]
    statusCode == 0
  }

  def "success when no files found"() {
    given:
    def sortCommand = newSortCommand()

    when:
    int statusCode = runSortCommand(sortCommand, '-m', 'check', dir.toString())

    then:
    sortCommand.mode == Mode.CHECK
    sortCommand.paths == [dir]
    statusCode == 0
  }

  def "can configure a block in #fileName"() {
    given:
    def buildScript = dir.resolve(fileName)
    Files.writeString(buildScript, '''\
      configurations {
        create("z")
        create("a")
      }
      '''.stripIndent())
    def sortCommand = newSortCommand()

    when:
    int statusCode = runSortCommand(
      sortCommand,
      '--block', 'configurations',
      buildScript.toString(),
    )

    then:
    statusCode == 0
    Files.readString(buildScript) == '''\
      configurations {
        create("a")
        create("z")
      }
      '''.stripIndent()

    where:
    fileName << ['build.gradle', 'build.gradle.kts']
  }

  def "rejects invalid block path '#blockPath'"() {
    given:
    def sortCommand = newSortCommand()

    when:
    sortCommand.parse(['--block', blockPath, dir.toString()] as String[], null)

    then:
    def error = thrown(UsageError)
    error.message == 'Each block path must contain one or more non-blank segments separated by dots.'

    where:
    blockPath << [
      '',
      ' ',
      '.dependencies',
      'dependencies.',
      'dependencies..constraints',
      'dependencies. .constraints',
    ]
  }

  def "fails with no paths passed in"() {
    given:
    def sortCommand = newSortCommand()

    when:
    int statusCode = runSortCommand(sortCommand, '-m', 'check')

    then:
    sortCommand.mode == Mode.CHECK
    statusCode == 1
  }

  private static int runSortCommand(SortCommand sortCommand, String[] args) {
    int statusCode = 0
    try {
      sortCommand.parse(args, null)
    } catch (ProgramResult result) {
      statusCode = result.statusCode
    } catch (UsageError result) {
      statusCode = result.statusCode
    }
    return statusCode
  }

  private static SortCommand newSortCommand() {
    return new SortCommand(
      FileSystems.getDefault(),
      BuildDotGradleFinder.Factory.Default.INSTANCE
    )
  }
}
