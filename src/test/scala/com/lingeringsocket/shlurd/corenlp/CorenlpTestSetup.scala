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
package com.lingeringsocket.shlurd.corenlp

import com.lingeringsocket.shlurd.parser._

import edu.stanford.nlp.simple._
import edu.stanford.nlp.trees._
import edu.stanford.nlp.ling._
import edu.stanford.nlp.simple.Document

import scala.collection.JavaConverters._

import java.util._

import SprUtils._
import SprPennTreebankLabels._

class CorenlpTestSetup
{
  SprParser.setStrategy(CorenlpParsingStrategy)
}

class CorenlpTokenizedSentence(val corenlpSentence : Sentence)
    extends SprTokenizedSentence
{
  override def text = corenlpSentence.text

  override def tokens = corenlpSentence.originalTexts.asScala

  def lemmas = corenlpSentence.lemmas.asScala

  def incomingDeps =
  {
    val props = new Properties
    props.setProperty(
      "depparse.model",
      "edu/stanford/nlp/models/parser/nndep/english_SD.gz")
    corenlpSentence.incomingDependencyLabels(props).asScala.map(_.orElse(""))
  }
}

class CorenlpTokenizer extends SprTokenizer
{
  override def tokenize(input : String) : Seq[CorenlpTokenizedSentence] =
  {
    val doc = new Document(input)
    doc.sentences.asScala.map(sentence => {
      new CorenlpTokenizedSentence(sentence)
    })
  }
}

class CorenlpTreeWrapper(
  corenlp : Tree, tokens : Seq[String], lemmas : Seq[String],
  incomingDeps : Seq[String])
    extends SprAbstractSyntaxTree
{
  private val wrappedChildren =
    corenlp.children.map(
      new CorenlpTreeWrapper(_, tokens, lemmas, incomingDeps))

  override def label =
    corenlp.label.value.split("-").head

  override def tags =
    corenlp.label.value.split("-").tail.toSet

  override def lemma =
    lemmas(corenlp.label.asInstanceOf[HasIndex].index)

  override def token = tokens(corenlp.label.asInstanceOf[HasIndex].index)

  override def incomingDep =
    incomingDeps(corenlp.label.asInstanceOf[HasIndex].index)

  override def children = wrappedChildren
}

object CorenlpParsingStrategy extends SprParsingStrategy
{
  override def newTokenizer = new CorenlpTokenizer

  override def isCoreNLP : Boolean = true

  override def prepareParser(
    sentence : SprTokenizedSentence,
    dump : Boolean) =
  {
    val tokens = sentence.tokens
    val sentenceString = sentence.text
    if (SprParser.isTerminator(tokens.last)) {
      prepareCorenlpFallbacks(
        sentenceString, tokens, false, dump, "CORENLP")
    } else {
      val questionString = sentenceString + LABEL_QUESTION_MARK
      prepareCorenlpFallbacks(
        questionString, tokens :+ LABEL_QUESTION_MARK,
        true, dump, "CORENLP")
    }
  }

  private def prepareCorenlpFallbacks(
    sentenceString : String, tokens : Seq[String],
    guessedQuestion : Boolean,
    dump : Boolean, dumpPrefix : String) =
  {
    val props = new Properties
    props.setProperty(
      "parse.model",
      "edu/stanford/nlp/models/lexparser/englishRNN.ser.gz")
    val propsSR = new Properties
    propsSR.setProperty(
      "parse.model",
      "edu/stanford/nlp/models/srparser/englishSR.ser.gz")
    val propsPCFG = new Properties
    propsPCFG.setProperty(
      "parse.model",
      "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz")
    val capitalizedString = capitalize(sentenceString)
    def main() = prepareCorenlp(
      capitalizedString, tokens, props, true, guessedQuestion,
      dump, dumpPrefix + " RNN")
    def fallbackSR() = prepareCorenlp(
      capitalizedString, tokens, propsSR, true, guessedQuestion,
      dump, dumpPrefix + " FALLBACK SR")
    def fallbackPCFG() = prepareCorenlp(
      capitalizedString, tokens, propsPCFG, false, guessedQuestion,
      dump, dumpPrefix + " FALLBACK PCFG")
    def fallbackSRCASELESS() = prepareCorenlp(
      sentenceString, tokens, propsSR, false, guessedQuestion,
      dump, dumpPrefix + " FALLBACK SR CASELESS")
    new SprFallbackParser(Seq(
      main, fallbackSR, fallbackPCFG, fallbackSRCASELESS))
  }

  private def prepareCorenlp(
    sentenceString : String, tokens : Seq[String], props : Properties,
    preDependencies : Boolean, guessedQuestion : Boolean,
    dump : Boolean, dumpPrefix : String) =
  {
    def corenlpParse() : SprSyntaxTree = {
      var deps : Seq[String] = Seq.empty
      val sentence = tokenizeCorenlp(sentenceString).head
      if (preDependencies) {
        // when preDependencies is requested, it's important to analyze
        // dependencies BEFORE parsing in order to get the best parse
        deps = sentence.incomingDeps
      }
      val corenlpTree = sentence.corenlpSentence.parse(props)
      if (dump) {
        println(dumpPrefix + " PARSE = " + corenlpTree)
      }
      corenlpTree.indexLeaves(0, true)
      if (!preDependencies) {
        // when preDependencies is not requested, it's important to analyze
        // dependencies AFTER parsing in order to get the best parse
        deps = sentence.incomingDeps
      }
      val lemmas = sentence.lemmas
      if (dump) {
        println(dumpPrefix + " DEPS = " + tokens.zip(deps))
      }
      SprSyntaxRewriter.rewriteAbstract(
        new CorenlpTreeWrapper(corenlpTree, tokens, lemmas, deps))
    }

    val syntaxTree = SprParser.cacheParse(
      SprParser.CacheKey(sentenceString, dumpPrefix), corenlpParse)
    val rewrittenTree = SprSyntaxRewriter.rewriteWarts(syntaxTree)
    if (dump) {
      println(dumpPrefix + " REWRITTEN SYNTAX = " + rewrittenTree)
    }
    new SprSingleParser(rewrittenTree, guessedQuestion)
  }

  private def tokenizeCorenlp(input : String)
      : Seq[CorenlpTokenizedSentence] =
  {
    val tokenizer = new CorenlpTokenizer
    tokenizer.tokenize(input)
  }
}
