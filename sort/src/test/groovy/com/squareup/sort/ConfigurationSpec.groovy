package com.squareup.sort

import spock.lang.Specification

import static com.google.common.truth.Truth.assertThat

final class ConfigurationSpec extends Specification {

  def "comparisons work"() {
    given:
    def configurations = [
      'implementation', 'api', 'releaseImplementation', 'debugApi', 'fooApi', 'kapt',
      'annotationProcessor', 'runtimeOnly', 'compileOnly', 'compileOnlyApi', 'testRuntimeOnly',
      'testCompileOnly', 'testImplementation', 'androidTestImplementation', 'androidTestRuntimeOnly',
      'antlr', 'foo', 'bar', 'baz', 'androidTestCompileOnly'
    ]

    when:
    configurations.sort(true) { left, right ->
      Configuration.stringCompare(left, right)
    }

    then:
    assertThat(configurations).containsExactly(
      'api',
      'debugApi',
      'fooApi',
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
      'androidTestCompileOnly',
      'androidTestRuntimeOnly',
      'antlr',
      'bar',
      'baz',
      'foo',
    ).inOrder()
  }
}
