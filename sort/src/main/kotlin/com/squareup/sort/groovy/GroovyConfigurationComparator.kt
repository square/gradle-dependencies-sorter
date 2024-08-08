package com.squareup.sort.groovy

import com.squareup.sort.Configuration

internal object GroovyConfigurationComparator :
  Comparator<MutableMap.MutableEntry<String, MutableList<GroovyDependencyDeclaration>>> {

  override fun compare(
    left: MutableMap.MutableEntry<String, MutableList<GroovyDependencyDeclaration>>,
    right: MutableMap.MutableEntry<String, MutableList<GroovyDependencyDeclaration>>
  ): Int = Configuration.stringCompare(left.key, right.key)
}
