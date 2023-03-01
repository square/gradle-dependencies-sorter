package com.squareup

import com.github.ajalt.clikt.core.ProgramResult
import com.squareup.sort.BuildDotGradleFinder
import com.squareup.sort.Mode
import com.squareup.sort.SortCommand
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Very basic spec as a toehold if we want to add more robust tests at the app level.
 *
 * @see <a href="https://picocli.info/#_testing_your_application">Testing your application.</a>
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
    sortCommand.paths == [dir.toString()]
    statusCode == 0
  }

  def "no files found"() {
    given:
    def sortCommand = newSortCommand()

    when:
    int statusCode = runSortCommand(sortCommand, '-m', 'check', dir.toString())

    then:
    sortCommand.mode == Mode.CHECK
    sortCommand.paths == [dir.toString()]
    statusCode == 1
  }

  def "error with no paths passed in"() {
    given:
    def sortCommand = newSortCommand()

    when:
    int statusCode = runSortCommand(sortCommand, '-m', 'check', dir.toString())

    then:
    sortCommand.mode == Mode.CHECK
    statusCode == 1
  }

  private int runSortCommand(SortCommand sortCommand, String[] args) {
    int statusCode = 0
    try {
      sortCommand.parse(args, null)
    } catch (ProgramResult result) {
      statusCode = result.statusCode
    }
    return statusCode
  }

  private SortCommand newSortCommand() {
    return new SortCommand(
      LoggerFactory.getLogger("Sorter"),
      FileSystems.getDefault(),
      BuildDotGradleFinder.Factory.Default.INSTANCE
    )
  }
}
