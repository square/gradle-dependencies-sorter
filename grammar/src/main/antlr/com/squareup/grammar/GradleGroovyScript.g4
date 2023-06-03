parser grammar GradleGroovyScript;

options { tokenVocab=GradleGroovyScriptLexer; }

script
    :   (dependencies|buildscript|text)* EOF
    ;

dependencies
    :   DEPENDENCIES (normalDeclaration|testFixturesDeclaration|platformDeclaration)* BRACE_CLOSE
    ;

buildscript
    :   BUILDSCRIPT BRACE_OPEN (dependencies|block|sea)* BRACE_CLOSE
    ;

block
    :   ID BRACE_OPEN (block|sea)* BRACE_CLOSE
    ;

normalDeclaration
    :   configuration PARENS_OPEN? quote? dependency quote? PARENS_CLOSE? closure?
    ;

testFixturesDeclaration
    :   configuration PARENS_OPEN? TEST_FIXTURES quote? dependency quote? PARENS_CLOSE PARENS_CLOSE? closure?
    ;

platformDeclaration
    :   configuration PARENS_OPEN? PLATFORM quote? dependency quote? PARENS_CLOSE PARENS_CLOSE? closure?
    ;

configuration
    :   ID
    ;

dependency
    :   externalDependency
    |   projectDependency
    |   fileDependency
    ;

externalDependency
    :   ID
    ;

projectDependency
    :   PROJECT PARENS_OPEN quote? ID quote? PARENS_CLOSE
    |   PROJECT PARENS_OPEN projectMapEntry+ PARENS_CLOSE
    ;

projectMapEntry
    :   WS? key=(CONFIGURATION|PATH) WS? quote? value=ID quote? WS? COMMA? WS?
    ;

fileDependency
    :   (FILE|FILES) PARENS_OPEN quote? ID quote? PARENS_CLOSE
    ;

closure
    :   BRACE_OPEN text+? BRACE_CLOSE
    ;

quote
    : QUOTE_SINGLE
    | QUOTE_DOUBLE
    ;

text
    : UNICODE_LATIN
    | ID
    | WS
    | DIGIT
    | FILE
    | FILES
    | EQUALS
    | SEMI
    | QUOTE_SINGLE
    | QUOTE_DOUBLE
    | BRACE_OPEN
    | BRACE_CLOSE
    | PARENS_OPEN
    | PARENS_CLOSE
    | BACKSLASH
    | PROJECT
    | COMMA
    ;

// Sea of crap I don't care about
sea
    : ID
    | EQUALS
    | QUOTE_SINGLE
    | QUOTE_DOUBLE
    | PARENS_OPEN
    | PARENS_CLOSE
    ;
