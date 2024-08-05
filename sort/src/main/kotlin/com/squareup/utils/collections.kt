package com.squareup.utils

internal inline fun <C> C.ifNotEmpty(block: (C) -> Unit) where C : Collection<*> {
  if (isNotEmpty()) {
    block(this)
  }
}
