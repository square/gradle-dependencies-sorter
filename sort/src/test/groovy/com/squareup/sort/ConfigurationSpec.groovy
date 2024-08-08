package com.squareup.sort

import spock.lang.Specification

import static com.google.common.truth.Truth.assertThat

final class ConfigurationSpec extends Specification {

  def "comparisons work"() {
    given:
    def configurations = [
      'implementation', 'api', 'releaseImplementation', 'debugApi', 'fooApi', 'kapt',
      'annotationProcessor', 'runtimeOnly', 'compileOnly', 'compileOnlyApi', 'testRuntimeOnly',
      'testCompileOnly', 'testImplementation', 'androidTestImplementation',
      'antlr', 'foo', 'bar', 'baz'
    ]

    when:
    configurations.sort(true) { left, right ->
      Configuration.stringCompare(left, right)
    }

    then:
    assertThat(configurations).containsExactly(
      'antlr',
      'bar',
      'baz',
      'foo',
      'api',
      'fooApi',
      'debugApi',
      'implementation',
      'releaseImplementation',
      'compileOnlyApi',
      'compileOnly',
      'runtimeOnly',
      'annotationProcessor',
      'kapt',
      'testImplementation',
      'testCompileOnly',
      'testRuntimeOnly',
      'androidTestImplementation',
    ).inOrder()
  }
}
