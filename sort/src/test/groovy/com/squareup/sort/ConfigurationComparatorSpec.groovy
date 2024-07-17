package com.squareup.sort


import spock.lang.Specification

import static com.google.common.truth.Truth.assertThat

final class ConfigurationComparatorSpec extends Specification {

  def "comparisons work"() {
    given:
    def configurations = [
        'implementation', 'api', 'releaseImplementation', 'debugApi', 'fooApi', 'kapt',
        'annotationProcessor', 'runtimeOnly', 'compileOnly', 'compileOnlyApi', 'testRuntimeOnly',
        'testCompileOnly', 'testImplementation', 'androidTestImplementation',
        'antlr', 'foo', 'bar', 'baz', 'ksp'
    ]

    when:
    configurations.sort(true) { left, right ->
      ConfigurationComparator.stringCompare(left, right)
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
        'ksp',
        'testImplementation',
        'testCompileOnly',
        'testRuntimeOnly',
        'androidTestImplementation',
    ).inOrder()
  }
}
