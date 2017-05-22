/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;

import java.util.EnumSet;

/**
 * @author Bas Leijdekkers
 */
public class RegExpLexerTest extends LexerTestCase {

  public void testAmpersand() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[a&&]", "CLASS_BEGIN ('[')\n" +
                    "CHARACTER ('a')\n" +
                    "CHARACTER ('&')\n" +
                    "CHARACTER ('&')\n" +
                    "CLASS_END (']')", lexer);
  }

  public void testQE() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\Q\r\n\\E", "QUOTE_BEGIN ('\\Q')\n" +
                         "CHARACTER ('\n" +
                         "')\n" +
                         "CHARACTER ('\\n')\n" +
                         "QUOTE_END ('\\E')", lexer);
  }

  public void testEditorReplacement() {
    RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.TRANSFORMATION_ESCAPES));
    String text = "\\U$1\\E\\u$3\\l$4\\L$2\\E";
    doTest(text, "CHAR_CLASS ('\\U')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('1')\n" +
                 "CHAR_CLASS ('\\E')\n" +
                 "CHAR_CLASS ('\\u')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('3')\n" +
                 "CHAR_CLASS ('\\l')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('4')\n" +
                 "CHAR_CLASS ('\\L')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('2')\n" +
                 "CHAR_CLASS ('\\E')", lexer);

    lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));

    doTest(text, "INVALID_CHARACTER_ESCAPE_TOKEN ('\\U')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('1')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\E')\n" +
                 "INVALID_UNICODE_ESCAPE_TOKEN ('\\u')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('3')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\l')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('4')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\L')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('2')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\E')", lexer);
  }

  public void testIntersection() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.NESTED_CHARACTER_CLASSES));
    doTest("[a&&]", "CLASS_BEGIN ('[')\n" +
                    "CHARACTER ('a')\n" +
                    "ANDAND ('&&')\n" +
                    "CLASS_END (']')", lexer);
  }

  public void testCarets() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("^\\^[^^]", "CARET ('^')\n" +
                       "ESC_CHARACTER ('\\^')\n" +
                       "CLASS_BEGIN ('[')\n" +
                       "CARET ('^')\n" +
                       "CHARACTER ('^')\n" +
                       "CLASS_END (']')", lexer);
  }

  public void testPosixBracketExpression() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.POSIX_BRACKET_EXPRESSIONS));
    doTest("[[:xdigit:]]", "CLASS_BEGIN ('[')\n" +
                           "BRACKET_EXPRESSION_BEGIN ('[:')\n" +
                           "NAME ('xdigit')\n" +
                           "BRACKET_EXPRESSION_END (':]')\n" +
                           "CLASS_END (']')", lexer);
  }

  public void testNegatedPosixBracketExpression() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.POSIX_BRACKET_EXPRESSIONS));
    doTest("[[:^xdigit:]]", "CLASS_BEGIN ('[')\n" +
                            "BRACKET_EXPRESSION_BEGIN ('[:')\n" +
                            "CARET ('^')\n" +
                            "NAME ('xdigit')\n" +
                            "BRACKET_EXPRESSION_END (':]')\n" +
                            "CLASS_END (']')", lexer);
  }

  public void testOctalWithoutLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.OCTAL_NO_LEADING_ZERO));
    doTest("\\0\\123[\\123]", "OCT_CHAR ('\\0')\n" +
                              "OCT_CHAR ('\\123')\n" +
                              "CLASS_BEGIN ('[')\n" +
                              "OCT_CHAR ('\\123')\n" +
                              "CLASS_END (']')", lexer);
  }

  public void testOctalFollowedByDigit() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.OCTAL_NO_LEADING_ZERO));
    doTest("\\39[\\39]", "OCT_CHAR ('\\3')\n" +
                         "CHARACTER ('9')\n" +
                         "CLASS_BEGIN ('[')\n" +
                         "OCT_CHAR ('\\3')\n" +
                         "CHARACTER ('9')\n" +
                         "CLASS_END (']')", lexer);
  }

  public void testOctalWithLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\0\\123[\\123]", "BAD_OCT_VALUE ('\\0')\n" +
                              "BACKREF ('\\1')\n" +
                              "CHARACTER ('2')\n" +
                              "CHARACTER ('3')\n" +
                              "CLASS_BEGIN ('[')\n" +
                              "REDUNDANT_ESCAPE ('\\1')\n" +
                              "CHARACTER ('2')\n" +
                              "CHARACTER ('3')\n" +
                              "CLASS_END (']')", lexer);
  }

  public void testNoNestedCharacterClasses1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[[\\]]", "CLASS_BEGIN ('[')\n" +
                     "CHARACTER ('[')\n" +
                     "ESC_CHARACTER ('\\]')\n" +
                     "CLASS_END (']')", lexer);
  }

  public void testNoNestedCharacterClasses2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[a-z&&[^aeuoi]]", "CLASS_BEGIN ('[')\n" +
                              "CHARACTER ('a')\n" +
                              "MINUS ('-')\n" +
                              "CHARACTER ('z')\n" +
                              "CHARACTER ('&')\n" +
                              "CHARACTER ('&')\n" +
                              "CHARACTER ('[')\n" +
                              "CHARACTER ('^')\n" +
                              "CHARACTER ('a')\n" +
                              "CHARACTER ('e')\n" +
                              "CHARACTER ('u')\n" +
                              "CHARACTER ('o')\n" +
                              "CHARACTER ('i')\n" +
                              "CLASS_END (']')\n" +
                              "CHARACTER (']')", lexer);
  }

  public void testNestedCharacterClasses1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.NESTED_CHARACTER_CLASSES));
    doTest("[a-z&&[^aeuoi]]", "CLASS_BEGIN ('[')\n" +
                              "CHARACTER ('a')\n" +
                              "MINUS ('-')\n" +
                              "CHARACTER ('z')\n" +
                              "ANDAND ('&&')\n" +
                              "CLASS_BEGIN ('[')\n" +
                              "CARET ('^')\n" +
                              "CHARACTER ('a')\n" +
                              "CHARACTER ('e')\n" +
                              "CHARACTER ('u')\n" +
                              "CHARACTER ('o')\n" +
                              "CHARACTER ('i')\n" +
                              "CLASS_END (']')\n" +
                              "CLASS_END (']')", lexer);
  }

  public void testNestedCharacterClasses2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.NESTED_CHARACTER_CLASSES));
    doTest("[]]", "CLASS_BEGIN ('[')\n" +
                  "CHARACTER (']')\n" +
                  "CLASS_END (']')", lexer);
  }

  @Override
  protected Lexer createLexer() {
    return null;
  }

  @Override
  protected String getDirPath() {
    return "";
  }
}
