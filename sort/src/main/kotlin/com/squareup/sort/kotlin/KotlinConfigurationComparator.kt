package com.squareup.sort.kotlin

import com.squareup.sort.Configuration

internal object KotlinConfigurationComparator :
  Comparator<MutableMap.MutableEntry<String, MutableList<KotlinDependencyDeclaration>>> {

  override fun compare(
    left: MutableMap.MutableEntry<String, MutableList<KotlinDependencyDeclaration>>,
    right: MutableMap.MutableEntry<String, MutableList<KotlinDependencyDeclaration>>
  ): Int = Configuration.stringCompare(left.key, right.key)
}
