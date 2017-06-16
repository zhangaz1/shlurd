// shlurd:  a limited understanding of small worlds
// Copyright 2017-2017 John V. Sichi
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
package shlurd.parser

import edu.stanford.nlp.simple._
import edu.stanford.nlp.trees._
import edu.stanford.nlp.ling._
import edu.stanford.nlp.simple.Document

import scala.collection.JavaConverters._

trait ShlurdParser
{
  def parseOne() : ShlurdSentence

  def parseFirst() : ShlurdSentence

  def parseAll() : Seq[ShlurdSentence]
}

class ShlurdSingleParser(
  tree : Tree, lemmas : Seq[String], implicitQuestion : Boolean)
    extends ShlurdParser
{
  private def getLabel(tree : Tree) : String =
  {
    tree.label.value
  }

  private def hasLabel(tree : Tree, label : String) : Boolean =
  {
    getLabel(tree) == label
  }

  private def isVerb(pt : Tree) : Boolean =
  {
    getLabel(pt).startsWith("VB")
  }

  private def isParticipleOrGerund(verbal : Tree) : Boolean =
  {
    getLabel(verbal) match {
      case "VBG" | "VBN" => {
        true
      }
      case _ => {
        false
      }
    }
  }

  private def isNounPhrase(np : Tree) : Boolean =
  {
    hasLabel(np, "NP")
  }

  private def isVerbPhrase(vp : Tree) : Boolean =
  {
    hasLabel(vp, "VP")
  }

  private def isNoun(pt : Tree) : Boolean =
  {
    getLabel(pt).startsWith("NN")
  }

  private def isPronoun(pt : Tree) : Boolean =
  {
    getLabel(pt).startsWith("PRP")
  }

  private def isAdjective(pt : Tree) : Boolean =
  {
    getLabel(pt).startsWith("JJ")
  }

  private def isAdverb(pt : Tree) : Boolean =
  {
    getLabel(pt).startsWith("RB")
  }

  private def isPreposition(pt : Tree) : Boolean =
  {
    getLabel(pt).startsWith("IN")
  }

  private def hasTerminalLabel(
    tree : Tree, label : String, terminalLabel : String) : Boolean =
  {
    tree.isPreTerminal && hasLabel(tree, label) &&
      hasLabel(tree.firstChild, terminalLabel)
  }

  private def expectRoot(tree : Tree, implicitQuestion : Boolean) =
  {
    if (hasLabel(tree, "ROOT")) {
      assert(tree.numChildren == 1)
      expectSentence(tree.firstChild, implicitQuestion)
    } else {
      ShlurdUnknownSentence
    }
  }

  private def truncatePunctuation(
    tree : Tree, punctuationMarks : Iterable[String]) : Array[Tree] =
  {
    val children = tree.children
    if (punctuationMarks.exists(punctuation =>
      hasTerminalLabel(children.last, ".", punctuation)))
    {
      children.dropRight(1)
    } else {
      children
    }
  }

  private def isImperative(children : Array[Tree]) =
  {
    (children.size == 1) && hasLabel(children.head, "VP")
  }

  private def expectSentence(tree : Tree, implicitQuestion : Boolean) =
  {
    if (hasLabel(tree, "S")) {
      val isQuestion =
        hasTerminalLabel(tree.children.last, ".", "?") && !implicitQuestion
      val children = truncatePunctuation(tree, Seq(".", "!", "?"))
      if (isImperative(children)) {
        expectCommand(children.head)
      } else if (children.size == 2) {
        val np = children.head
        val vp = children.last
        if (isNounPhrase(np) && isVerbPhrase(vp)) {
          expectPredicateSentence(np, vp, isQuestion)
        } else {
          ShlurdUnknownSentence
        }
      } else {
        ShlurdUnknownSentence
      }
    } else if (hasLabel(tree, "SQ")) {
      val children = truncatePunctuation(tree, Seq("?"))
      if (isImperative(children)) {
        expectCommand(children.head)
      } else  if (children.size == 3) {
        val verbHead = children(0)
        val np = children(1)
        val ap = children(2)
        if (isVerb(verbHead) && hasTerminalLemma(verbHead, "be")) {
          ShlurdPredicateSentence(
            expectPredicate(np, ap),
            MOOD_INTERROGATIVE)
        } else {
          ShlurdUnknownSentence
        }
      } else {
        ShlurdUnknownSentence
      }
    } else {
      ShlurdUnknownSentence
    }
  }

  private def expectPredicateSentence(
    np : Tree, vp : Tree, isQuestion : Boolean) =
  {
    val verbHead = vp.firstChild
    if (isVerb(verbHead) && hasTerminalLemma(verbHead, "be")) {
      if (vp.numChildren > 3) {
        ShlurdUnknownSentence
      } else {
        val predicate = expectPredicate(np, vp.lastChild)
        if (isQuestion) {
          ShlurdPredicateSentence(predicate, MOOD_INTERROGATIVE)
        } else {
          val mood = {
            if (vp.numChildren == 3) {
              val adverb = vp.children()(1)
              if (isAdverb(adverb) && hasTerminalLemma(adverb, "not")) {
                MOOD_INDICATIVE_NEGATIVE
              } else {
                MOOD_INDICATIVE_POSITIVE
              }
            } else {
              MOOD_INDICATIVE_POSITIVE
            }
          }
          ShlurdPredicateSentence(predicate, mood)
        }
      }
    } else {
      ShlurdUnknownSentence
    }
  }

  private def expectCommand(vp : Tree) =
  {
    if (vp.numChildren == 2) {
      val state = expectVerbState(vp.firstChild)
      val subject = expectReference(vp.lastChild)
      ShlurdStateChangeCommand(
        ShlurdStatePredicate(subject, state))
    } else {
      ShlurdUnknownSentence
    }
  }

  private def expectReference(np : Tree) : ShlurdReference =
  {
    if (isNounPhrase(np)) {
      val first = np.firstChild
      if (np.numChildren == 1) {
        expectReference(first)
      } else {
        val (determiner, components) = {
          if (hasLabel(first, "DT")) {
            (expectDeterminer(first.firstChild), np.children.drop(1))
          } else {
            (DETERMINER_UNSPECIFIED, np.children)
          }
        }
        if ((components.size == 2) && isPronoun(components.head)) {
          val pronounReference = pronounFor(
            getLemma(components.head.firstChild))
          val entityReference = expectNounReference(components.last, determiner)
          ShlurdGenitiveReference(pronounReference, entityReference)
        } else if (components.forall(c => isNoun(c) || isAdjective(c))) {
          val entityReference = expectNounReference(components.last, determiner)
          if (components.size > 1) {
            ShlurdQualifiedReference(
              entityReference,
              components.dropRight(1).map(c => getWord(c.firstChild)))
          } else {
            entityReference
          }
        } else {
          ShlurdUnknownReference
        }
      }
    } else if (isNoun(np)) {
      ShlurdEntityReference(
        getWord(np.firstChild),
        DETERMINER_UNSPECIFIED,
        getCount(np))
    } else if (isPronoun(np)) {
      pronounFor(getLemma(np.firstChild))
    } else {
      ShlurdUnknownReference
    }
  }

  private def pronounFor(lemma : String) =
  {
    val person = lemma match {
      case "i" | "me" | "we" | "my" | "our" | "mine" | "ours" => PERSON_FIRST
      case "you" | "your" | "yours" => PERSON_SECOND
      case _ => PERSON_THIRD
    }
    val count = lemma match {
      case "we" | "us" | "they" | "our" | "their" => COUNT_PLURAL
      case _ => COUNT_SINGULAR
    }
    val gender = lemma match {
      case "he" | "him" | "his" => GENDER_M
      case "she" | "her" | "hers" => GENDER_F
      case _ => GENDER_N
    }
    ShlurdPronounReference(person, gender, count)
  }

  private def expectDeterminer(leaf : Tree) =
  {
    getLemma(leaf) match {
      case "no" => DETERMINER_NONE
      case "all" => DETERMINER_ALL
      case "a" => DETERMINER_NONSPECIFIC
      case "the" => DETERMINER_UNIQUE
      case _ => DETERMINER_ANY
    }
  }

  private def expectNounReference(
    pt : Tree, determiner : ShlurdDeterminer) =
  {
    // we allow mislabeled adjectives to handle
    // cases like "roll up the blind"
    if (isNoun(pt) || isAdjective(pt)) {
      val noun = pt.firstChild
      ShlurdEntityReference(
        getWord(noun),
        determiner,
        getCount(pt))
    } else {
      ShlurdUnknownReference
    }
  }

  private def hasTerminalLemma(tree : Tree, lemma : String) =
  {
    tree.isPreTerminal && (getLemma(tree.firstChild) == lemma)
  }

  private def getLemma(leaf : Tree) : String =
  {
    lemmas(leaf.label.asInstanceOf[HasIndex].index).toLowerCase
  }

  private def getCount(tree : Tree) : ShlurdCount =
  {
    if (getLabel(tree).endsWith("S")) {
      COUNT_PLURAL
    } else {
      COUNT_SINGULAR
    }
  }

  private def getWord(tree : Tree) =
  {
    ShlurdWord(getLabel(tree), getLemma(tree))
  }

  private def expectVerbState(pt : Tree) =
  {
    if (pt.isPreTerminal && isVerb(pt)) {
      ShlurdPropertyState(getWord(pt.firstChild))
    } else {
      ShlurdUnknownState
    }
  }

  private def expectAdjectiveState(ap : Tree) =
  {
    if ((isAdjective(ap) || isParticipleOrGerund(ap)) && ap.isPreTerminal) {
      ShlurdPropertyState(getWord(ap.firstChild))
    } else {
      ShlurdUnknownState
    }
  }

  private def expectAdverbState(ap : Tree) =
  {
    if (isAdverb(ap) && ap.isPreTerminal) {
      ShlurdPropertyState(getWord(ap.firstChild))
    } else {
      ShlurdUnknownState
    }
  }

  private def expectPrepositionalState(ap : Tree) : ShlurdState =
  {
    val prep = ap.firstChild
    if ((ap.numChildren == 2) && isPreposition(prep)) {
      val prepLemma = getLemma(prep.firstChild)
      val locative = prepLemma match {
        case "in" | "inside" | "within" => LOC_INSIDE
        case "outside" => LOC_OUTSIDE
        case "at" => LOC_AT
        case "near" | "nearby" => LOC_NEAR
        case "on" => LOC_ON
        case "above" | "over" => LOC_ABOVE
        case "below" | "under" | "beneath" | "underneath" => LOC_BELOW
        case "behind" => LOC_BEHIND
        case _ => return ShlurdUnknownState
      }
      ShlurdLocationState(locative, expectReference(ap.lastChild))
    } else {
      ShlurdUnknownState
    }
  }

  private def expectPredicate(np : Tree, complement : Tree) =
  {
    val subject = expectReference(np)
    if (hasLabel(complement, "ADJP")) {
      val state = expectAdjectiveState(complement.firstChild)
      ShlurdStatePredicate(subject, state)
    } else if (hasLabel(complement, "ADVP")) {
      val state = {
        if (complement.numChildren == 1) {
          expectAdverbState(complement.firstChild)
        } else {
          expectPrepositionalState(complement)
        }
      }
      ShlurdStatePredicate(subject, state)
    } else if (hasLabel(complement, "VP")) {
      // TODO:  ambiguity for action (passive construction) vs
      // state (participial adjective)
      if (complement.isPrePreTerminal) {
        ShlurdStatePredicate(
          subject, expectVerbState(complement.firstChild))
      } else {
        ShlurdUnknownPredicate
      }
    } else {
      ShlurdUnknownPredicate
    }
  }

  override def parseOne() = expectRoot(tree, implicitQuestion)

  override def parseFirst() = parseOne

  override def parseAll() = Seq(parseOne)
}

class ShlurdMultipleParser(singles : Seq[ShlurdSingleParser])
    extends ShlurdParser
{
  override def parseOne() : ShlurdSentence =
  {
    assert(singles.size == 1)
    parseFirst
  }

  override def parseFirst() = singles.head.parseOne

  override def parseAll() = singles.map(_.parseOne)
}

object ShlurdParser
{
  private def dump(sentence : Sentence)
  {
    val tree = sentence.parse
    println("TREE = " + tree)
    Range(0, sentence.length).foreach(i => {
      println("DEP = " + sentence.incomingDependencyLabel(i))
    })
  }

  private def tokenize(input : String) : Seq[Sentence] =
  {
    val doc = new Document(input)
    doc.sentences.asScala
  }

  private def newParser(
    sentence : Sentence, tree : Tree, implicitQuestion : Boolean)
      : ShlurdSingleParser =
  {
    tree.indexLeaves(0, true)
    val lemmas = sentence.lemmas.asScala
    new ShlurdSingleParser(tree, lemmas, implicitQuestion)
  }

  private def prepareOne(sentence : Sentence) : ShlurdSingleParser =
  {
    val tree = sentence.parse
    if (tree.preTerminalYield.asScala.last.value ==  ".") {
      newParser(sentence, tree, false)
    } else {
      val question = tokenize(sentence.text + "?").head
      newParser(question, question.parse, true)
    }
  }

  def apply(input : String) : ShlurdParser =
  {
    val sentences = tokenize(input)
    if (sentences.size == 1) {
      prepareOne(sentences.head)
    } else {
      new ShlurdMultipleParser(sentences.map(prepareOne(_)))
    }
  }
}
