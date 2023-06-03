package com.squareup.grammar

import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.atn.ATNSimulator
import org.antlr.v4.runtime.dfa.DFA

final class SimpleANTLRErrorListener implements ANTLRErrorListener {
  private Closure<RuntimeException> onError

  SimpleANTLRErrorListener(Closure<RuntimeException> onError) {
    this.onError = onError;
  }

  @Override
  void syntaxError(Recognizer<?, ? extends ATNSimulator> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
    onError.call(new GroovyRuntimeException("Syntax error: $msg"))
  }

  @Override
  void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
  }

  @Override
  void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
  }

  @Override
  void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
  }
}
