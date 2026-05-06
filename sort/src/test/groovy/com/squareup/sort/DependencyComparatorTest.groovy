package com.squareup.sort

import cash.grammar.kotlindsl.model.DependencyDeclaration.Capability
import cash.grammar.kotlindsl.model.DependencyDeclaration.Identifier
import cash.grammar.kotlindsl.model.DependencyDeclaration.Type
import com.squareup.sort.kotlin.KotlinDependencyDeclaration
import spock.lang.Specification

class DependencyComparatorTest extends Specification {

  def "can sort dependency declarations"() {
    given:
    def deps = [
      moduleDependency('implementation', '"heart:of-gold:1.0"', 'implementation("heart:of-gold:1.0")'),
      moduleDependency('implementation', '"b:1.0"', 'implementation("b:1.0")'),
      moduleDependency('implementation', '"a:1.0"', 'implementation("a:1.0")'),
      moduleDependency('implementation', 'deps.foo', 'implementation(deps.foo)'),
      moduleDependency('implementation', 'deps.bar', 'implementation(deps.bar)',
        '  /*\n   * Here\'s a multiline comment.\n   */'),
      projectDependency('implementation', '":milliways"', 'implementation(project(":milliways"))'),
      fileDependency('implementation', '"libs/whatever.jar"', 'implementation(files("libs/whatever.jar"))'),
      projectDependency('implementation', 'project.sourceSets["test"].output', 'implementation(project.sourceSets["test"].output)')
    ]

    when:
    Collections.sort(deps, new DependencyComparator())

    then:
    deps[0].base.identifier.path == '":milliways"'
    deps[1].base.identifier.path == 'project.sourceSets["test"].output'
    deps[2].base.identifier.path == '"libs/whatever.jar"'
    deps[3].base.identifier.path == '"a:1.0"'
    deps[4].base.identifier.path == '"b:1.0"'
    deps[5].base.identifier.path == '"heart:of-gold:1.0"'
    deps[6].base.identifier.path == 'deps.bar'
    deps[7].base.identifier.path == 'deps.foo'
  }

  def "sorting dependency declarations is stable"() {
    given:
    def deps = [
      moduleDependency('implementation', '"heart:of-gold:1.0"', 'implementation("heart:of-gold:1.0")'),
      moduleDependency('implementation', '"b:1.0"', 'implementation("b:1.0")'),
      moduleDependency('implementation', '"a:1.0"', 'implementation("a:1.0")'),
      moduleDependency('implementation', 'deps.foo', 'implementation(deps.foo)'),
      moduleDependency('implementation', 'deps.bar', 'implementation(deps.bar)',
        '  /*\n   * Here\'s a multiline comment.\n   */'),
      projectDependency('implementation', '":milliways"', 'implementation(project(":milliways"))'),
      fileDependency('implementation', '"libs/whatever.jar"', 'implementation(files("libs/whatever.jar"))'),
    ]

    when:
    Collections.sort(deps, new DependencyComparator())
    def firstPass = deps.collect { it.base.identifier.path }

    // Sort again to verify stability
    Collections.sort(deps, new DependencyComparator())
    def secondPass = deps.collect { it.base.identifier.path }

    then:
    firstPass == secondPass
  }

  private static KotlinDependencyDeclaration fileDependency(
    String configuration,
    String identifier,
    String fullText,
    String comment = null,
    Capability capability = Capability.DEFAULT
  ) {
    def base = new cash.grammar.kotlindsl.model.DependencyDeclaration(
      configuration,
      new Identifier(identifier),
      capability,
      Type.FILE,
      fullText,
      null, // producerConfiguration
      null, // classifier
      null, // ext
      comment, // precedingComment
      false, // isComplex
    )

    return new KotlinDependencyDeclaration(base)
  }

  private static KotlinDependencyDeclaration moduleDependency(
    String configuration,
    String identifier,
    String fullText,
    String comment = null,
    Capability capability = Capability.DEFAULT
  ) {
    def base = new cash.grammar.kotlindsl.model.DependencyDeclaration(
      configuration,
      new Identifier(identifier),
      capability,
      Type.MODULE,
      fullText,
      null, // producerConfiguration
      null, // classifier
      null, // ext
      comment, // precedingComment
      false, // isComplex
    )

    return new KotlinDependencyDeclaration(base)
  }

  private static KotlinDependencyDeclaration projectDependency(
    String configuration,
    String identifier,
    String fullText,
    String comment = null,
    Capability capability = Capability.DEFAULT
  ) {
    def base = new cash.grammar.kotlindsl.model.DependencyDeclaration(
      configuration,
      new Identifier(identifier),
      capability,
      Type.PROJECT,
      fullText,
      null, // producerConfiguration
      null, // classifier
      null, // ext
      comment, // precedingComment
      false, // isComplex
    )

    return new KotlinDependencyDeclaration(base)
  }
}
