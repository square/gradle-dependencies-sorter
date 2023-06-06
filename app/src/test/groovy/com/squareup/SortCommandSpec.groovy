package com.squareup

import com.squareup.sort.BuildDotGradleFinder
import com.squareup.sort.SortCommand
import org.slf4j.LoggerFactory
import picocli.CommandLine
import spock.lang.Specification

import java.nio.file.FileSystems

/**
 * Very basic spec as a toehold if we want to add more robust tests at the app level.
 *
 * @see <a href="https://picocli.info/#_testing_your_application">Testing your application.</a>
 */
final class SortCommandSpec extends Specification {

  def "can parse args"() {
    given:
    def commandLine = new CommandLine(newSortCommand())

    when:
    def parseResult = commandLine.parseArgs('-m', 'check', '-v')

    then:
    parseResult.expandedArgs() == ['-m', 'check', '-v']
  }

  private SortCommand newSortCommand() {
    return new SortCommand(
      FileSystems.getDefault(),
      Stub(BuildDotGradleFinder.Factory)
    )
  }
}
