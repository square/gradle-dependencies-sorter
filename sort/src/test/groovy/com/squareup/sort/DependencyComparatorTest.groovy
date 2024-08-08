package com.squareup.sort

import cash.grammar.kotlindsl.model.DependencyDeclaration.Capability
import cash.grammar.kotlindsl.model.DependencyDeclaration.Type
import com.squareup.sort.kotlin.KotlinDependencyDeclaration
import spock.lang.Specification
import cash.grammar.kotlindsl.model.DependencyDeclaration.Identifier

class DependencyComparatorTest extends Specification {

  def "can sort dependency declarations"() {
    given:
    def deps = [
      moduleDependency('implementation', '"heart:of-gold:1.0"', 'implementation("heart:of-gold:1.0")'),
      moduleDependency('implementation', '"b:1.0"', 'implementation("b:1.0")'),
      moduleDependency('implementation', '"a:1.0"', 'implementation("a:1.0")'),
      moduleDependency('implementation', 'deps.foo', 'implementation(deps.foo)'),
      moduleDependency('implementation', 'deps.bar', 'implementation(deps.bar)', '  /*\n   * Here\'s a multiline comment.\n   */'),
      projectDependency('implementation', '":milliways"', 'implementation(project(":milliways"))'),
    ]

    when:
    Collections.sort(deps, new DependencyComparator())

    then:
    deps[0].base.identifier.path == '":milliways"'
    deps[1].base.identifier.path == '"a:1.0"'
    deps[2].base.identifier.path == '"b:1.0"'
    deps[3].base.identifier.path == '"heart:of-gold:1.0"'
    deps[4].base.identifier.path == 'deps.bar'
    deps[5].base.identifier.path == 'deps.foo'
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
      comment
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
      comment
    )

    return new KotlinDependencyDeclaration(base)
  }
}
