// shlurd:  a limited understanding of small worlds
// Copyright 2017-2018 John V. Sichi
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.lingeringsocket.shlurd.parser

import SprPennTreebankLabels._

import org.specs2.mutable._

class SprTokenizerSpec extends Specification
{
  private def tokenize(input : String, tokens : Seq[String]) =
  {
      val tokenizer = new SprIxaTokenizer
      val sentences = tokenizer.tokenize(input)
      sentences.size must be equalTo 1
      val sentence = sentences.head
      sentence.text must be equalTo input
      sentence.tokens.map(_.text) must be equalTo tokens
  }

  "SprTokenizer" should
  {
    "tokenize simple sentence" in
    {
      tokenize(
        "Seven ate 9.",
        Seq("Seven", "ate", "9", "."))
    }

    "tokenize underscores" in
    {
      tokenize(
        "I am so_not_doing_this.",
        Seq("I", "am", "so_not_doing_this", "."))
    }

    "tokenize parentheses" in
    {
      tokenize(
        "the (gray vehicle)'s engine is broken",
        Seq("the", LABEL_LPAREN, "gray", "vehicle", LABEL_RPAREN,
          "'s", "engine", "is", "broken"))
    }

    "tokenize curly brackets" in
    {
      tokenize(
        "the {gray vehicle}'s engine is broken",
        Seq("the", LABEL_LCURLY, "gray", "vehicle", LABEL_RCURLY,
          "'s", "engine", "is", "broken"))
    }

    "tokenize quotation" in
    {
      tokenize(
        "She said \"Eat my shorts. I love pie.\"",
        Seq("She", "said", LABEL_LQUOTE,
          "Eat", "my", "shorts", ".", "I", "love", "pie", ".",
          LABEL_RQUOTE))
    }
  }
}
