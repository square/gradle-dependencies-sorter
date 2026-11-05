package com.squareup.sort.groovy

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.misc.Interval

/**
 * Tracks which parts of a Groovy script are code, strings, or comments.
 *
 * The parser can mistake text inside a string, such as `"custom {}"`, for a real Gradle block. Marking strings and
 * comments lets the sorter ignore those false matches.
 *
 * ANTLR indexes Unicode code points, while JVM strings use UTF-16 offsets, so [charOffsets] translates between the two.
 */
internal class GroovySource(input: CharStream) {

  private val size = input.size()
  private val text = if (size == 0) "" else input.getText(Interval.of(0, size - 1))
  private val charOffsets = IntArray(size + 1)
  private val kinds = ByteArray(size)

  init {
    var charOffset = 0
    for (index in 0 until size) {
      charOffsets[index] = charOffset
      charOffset += Character.charCount(text.codePointAt(charOffset))
    }
    charOffsets[size] = text.length

    var index = 0
    while (index < size) {
      index = when {
        startsWith(index, "//") -> markLineComment(index)
        startsWith(index, "/*") -> markBlockComment(index)
        startsWith(index, "$/") -> markDollarSlashyString(index)
        startsWith(index, "'''") -> markQuotedString(index, "'''")
        startsWith(index, "\"\"\"") -> markQuotedString(index, "\"\"\"")
        codePointAt(index) == '\''.code -> markQuotedString(index, "'")
        codePointAt(index) == '"'.code -> markQuotedString(index, "\"")
        codePointAt(index) == '/'.code && isSlashyStringStart(index) -> markSlashyString(index)
        else -> index + 1
      }
    }
  }

  /** Returns the Unicode code point at an ANTLR source index. */
  fun codePointAt(index: Int): Int {
    return text.codePointAt(charOffsets[index])
  }

  fun isCode(index: Int): Boolean = index in 0 until size && kinds[index] == CODE

  fun isComment(index: Int): Boolean = index in 0 until size && kinds[index] == COMMENT

  fun isString(index: Int): Boolean = index in 0 until size && kinds[index] == STRING

  /** Returns true when the inclusive range contains a line break outside strings and comments. */
  fun hasCodeLineBreak(startIndex: Int, stopIndex: Int): Boolean {
    if (startIndex > stopIndex) return false
    return (startIndex..stopIndex).any { index ->
      isCode(index) && (codePointAt(index) == '\n'.code || codePointAt(index) == '\r'.code)
    }
  }

  /** Returns the first code point outside strings and comments in the inclusive range. */
  fun firstCodePoint(startIndex: Int, stopIndex: Int): Int? {
    if (startIndex > stopIndex) return null
    return (startIndex..stopIndex).firstOrNull(::isCode)?.let(::codePointAt)
  }

  /** Returns the last code point outside strings and comments in the inclusive range. */
  fun lastCodePoint(startIndex: Int, stopIndex: Int): Int? {
    if (startIndex > stopIndex) return null
    return (stopIndex downTo startIndex).firstOrNull(::isCode)?.let(::codePointAt)
  }

  /** Returns source text for an inclusive range of ANTLR source indexes. */
  fun text(startIndex: Int, stopIndex: Int): String {
    if (startIndex > stopIndex) return ""
    return text.substring(charOffsets[startIndex], charOffsets[stopIndex + 1])
  }

  /** Marks a `//` comment and returns the first source index after it. */
  private fun markLineComment(startIndex: Int): Int {
    var end = startIndex
    while (end < size && codePointAt(end) != '\n'.code && codePointAt(end) != '\r'.code) end++
    mark(startIndex, end - 1, COMMENT)
    return end
  }

  /** Marks a `/* ... */` comment and returns the first source index after it. */
  private fun markBlockComment(startIndex: Int): Int {
    var end = startIndex + 2
    while (end < size - 1 && !startsWith(end, "*/")) end++
    end = if (end < size - 1) end + 2 else size
    mark(startIndex, end - 1, COMMENT)
    return end
  }

  /** Marks a single- or triple-quoted string and returns the first source index after it. */
  private fun markQuotedString(startIndex: Int, delimiter: String): Int {
    val delimiterLength = delimiter.codePointCount(0, delimiter.length)
    var end = startIndex + delimiterLength
    while (end < size) {
      if (startsWith(end, delimiter) && !isEscaped(end)) {
        end += delimiterLength
        mark(startIndex, end - 1, STRING)
        return end
      }
      if (delimiterLength == 1 && codePointAt(end) == '\\'.code) end++
      end++
    }
    mark(startIndex, size - 1, STRING)
    return size
  }

  /** Marks a `/.../` slashy string and returns the first source index after it. */
  private fun markSlashyString(startIndex: Int): Int {
    var end = startIndex + 1
    while (end < size) {
      if (codePointAt(end) == '/'.code && !isEscaped(end)) {
        end++
        mark(startIndex, end - 1, STRING)
        return end
      }
      end++
    }
    mark(startIndex, size - 1, STRING)
    return size
  }

  /** Marks a `$/.../$` string and returns the first index after it, skipping `/$$` escaped terminators. */
  private fun markDollarSlashyString(startIndex: Int): Int {
    var end = startIndex + 2
    while (end < size - 1) {
      if (startsWith(end, "/$$")) {
        end += 3
        continue
      }
      if (startsWith(end, "/$")) {
        end += 2
        mark(startIndex, end - 1, STRING)
        return end
      }
      end++
    }
    mark(startIndex, size - 1, STRING)
    return size
  }

  /**
   * Distinguishes a slashy-string opener from division using the preceding syntax.
   *
   * A command argument such as `println /ok/` also needs a plausible closing delimiter so separate division operators
   * are not paired across statements.
   */
  private fun isSlashyStringStart(index: Int): Boolean {
    var previous = index - 1
    var crossedLine = false
    while (previous >= 0 && Character.isWhitespace(codePointAt(previous))) {
      if (codePointAt(previous) == '\n'.code || codePointAt(previous) == '\r'.code) crossedLine = true
      previous--
    }
    if (previous < 0 || crossedLine) return true

    if (codePointAt(previous) in SLASHY_PREFIXES) return true
    if (!Character.isJavaIdentifierPart(codePointAt(previous))) return false

    var wordStart = previous
    while (wordStart > 0 && Character.isJavaIdentifierPart(codePointAt(wordStart - 1))) {
      wordStart--
    }
    if (text(wordStart, previous) in SLASHY_PREFIX_KEYWORDS) return true

    // A bare identifier at the start of a statement can take a slashy command argument, for example println /ok/.
    return isStatementStart(wordStart) && hasCommandSlashyTerminator(index)
  }

  /** Returns true when [index] follows a line break, opening brace, or semicolon. */
  private fun isStatementStart(index: Int): Boolean {
    var previous = index - 1
    while (previous >= 0 && Character.isWhitespace(codePointAt(previous))) {
      if (codePointAt(previous) == '\n'.code || codePointAt(previous) == '\r'.code) return true
      previous--
    }
    return previous < 0 ||
      codePointAt(previous) == '{'.code ||
      codePointAt(previous) == ';'.code
  }

  /** Returns true when an unescaped slash is followed by syntax that can end a command argument. */
  private fun hasCommandSlashyTerminator(startIndex: Int): Boolean {
    var end = startIndex + 1
    while (end < size) {
      if (codePointAt(end) == '/'.code && !isEscaped(end)) {
        var next = end + 1
        while (next < size && codePointAt(next) in HORIZONTAL_WHITESPACE) next++
        return next == size ||
          codePointAt(next) in SLASHY_SUFFIXES ||
          startsWith(next, "//") ||
          startsWith(next, "/*")
      }
      end++
    }
    return false
  }

  /** Returns true when [index] follows an odd-length run of backslashes. */
  private fun isEscaped(index: Int): Boolean {
    var backslashes = 0
    var cursor = index - 1
    while (cursor >= 0 && codePointAt(cursor) == '\\'.code) {
      backslashes++
      cursor--
    }
    return backslashes % 2 != 0
  }

  /** Tests a JVM string against the source's code-point indexes. */
  private fun startsWith(index: Int, value: String): Boolean {
    val valueSize = value.codePointCount(0, value.length)
    if (index + valueSize > size) return false
    var charOffset = 0
    for (offset in 0 until valueSize) {
      val expected = value.codePointAt(charOffset)
      if (codePointAt(index + offset) != expected) return false
      charOffset += Character.charCount(expected)
    }
    return true
  }

  /** Assigns [kind] to every source index in the inclusive range. */
  private fun mark(startIndex: Int, stopIndex: Int, kind: Byte) {
    if (startIndex > stopIndex) return
    for (index in startIndex..stopIndex) kinds[index] = kind
  }

  private companion object {
    const val CODE: Byte = 0
    const val COMMENT: Byte = 1
    const val STRING: Byte = 2

    val SLASHY_PREFIXES = setOf(
      '='.code,
      '('.code,
      '['.code,
      '{'.code,
      ','.code,
      ':'.code,
      ';'.code,
      '!'.code,
      '&'.code,
      '|'.code,
      '?'.code,
      '+'.code,
      '-'.code,
      '*'.code,
      '%'.code,
      '~'.code,
    )
    val SLASHY_PREFIX_KEYWORDS = setOf(
      "assert",
      "case",
      "return",
      "throw",
      "yield",
    )
    val HORIZONTAL_WHITESPACE = setOf(' '.code, '\t'.code, '\u000C'.code)
    val SLASHY_SUFFIXES = setOf(
      '\n'.code,
      '\r'.code,
      ')'.code,
      ']'.code,
      '}'.code,
      ','.code,
      ';'.code,
      '.'.code,
      '?'.code,
      '*'.code,
      '+'.code,
      '-'.code,
      '%'.code,
      '&'.code,
      '|'.code,
      '^'.code,
    )
  }
}
