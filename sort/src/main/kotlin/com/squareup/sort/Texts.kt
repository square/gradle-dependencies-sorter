package com.squareup.sort

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token

internal data class Texts(
  val comment: String?,
  val declarationText: String,
)

private val lineBreak = Regex("""\r\n|(?<!\r)\n|\r(?!\n)""")
private val nextLineIsBlank = Regex("^[\t ]*(?:${lineBreak.pattern})")

internal fun missingBlankLineReplacements(
  declarations: List<Triple<String, Token, Token>>,
  tokens: CommonTokenStream,
  lineSeparator: String,
): List<Pair<Token, String>>? {
  val replacements = mutableListOf<Pair<Token, String>>()
  for ((first, second) in declarations.zipWithNext()) {
    if (first.first == second.first) continue
    val between = (first.third.tokenIndex + 1 until second.second.tokenIndex).map(tokens::get)
    if (between.any {
        it.channel == Token.DEFAULT_CHANNEL && it.text != ";" && !it.text.all(Char::isWhitespace)
      }) continue
    val boundary = between.firstOrNull {
      it.text.any { char -> char == '\r' || char == '\n' } &&
        (it.text.all(Char::isWhitespace) || it.text.endsWith('\r') || it.text.endsWith('\n'))
    } ?: return null
    val following = between.dropWhile { it !== boundary }.joinToString(separator = "") { it.text }
    val firstLineEnd = lineBreak.find(following)!!.range.last + 1
    if (!nextLineIsBlank.containsMatchIn(following.substring(firstLineEnd))) {
      val insertionPoint = lineBreak.find(boundary.text)!!.range.last + 1
      replacements += boundary to (
        boundary.text.substring(0, insertionPoint) + lineSeparator + boundary.text.substring(insertionPoint)
        )
    }
  }
  return replacements
}
