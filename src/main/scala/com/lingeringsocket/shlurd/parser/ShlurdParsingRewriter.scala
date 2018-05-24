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

import ShlurdParseUtils._
import ShlurdEnglishLemmas._

class ShlurdParsingRewriter(analyzer : ShlurdSyntaxAnalyzer)
  extends SilPhraseRewriter
{
  def parseSentence(sentenceSyntaxTree : ShlurdSyntaxTree) : SilSentence =
  {
    val forceSQ = sentenceSyntaxTree.firstChild.firstChild.isBeingVerb
    val expected = SilExpectedSentence(sentenceSyntaxTree, forceSQ)
    val phrase = rewrite[SilSentence](replaceAllPhrases, expected, true)
    val completed = rewrite(replaceUnresolvedWithUnrecognized, phrase)
    if (!completed.hasUnknown) {
      query(validateResult, completed)
    }
    completed match {
      case sentence : SilSentence => sentence
      case _ => SilUnrecognizedSentence(sentenceSyntaxTree)
    }
  }

  private def validateResult = queryMatcher {
    case transformedPhrase : SilTransformedPhrase => {
      if (!transformedPhrase.hasSyntaxTree) {
        throw new AssertionError("Syntax lost for " + transformedPhrase)
      }
    }
  }

  private def replaceAllPhrases = combineRules(
    replaceExpectedSentence,
    replaceExpectedSBARQ,
    replaceExpectedSQ,
    replaceAmbiguousSentence,
    replaceUnresolvedPredicate,
    replaceExpectedState,
    replaceExpectedReference)

  private def replaceExpectedSentence = replacementMatcher {
    case SilExpectedSentence(sentence : SptS, forceSQ) => {
      if (forceSQ) {
        analyzer.analyzeSQ(sentence, forceSQ)
      } else {
        analyzer.analyzeSentence(sentence)
      }
    }
  }

  private def replaceExpectedReference = replacementMatcher {
    case SilExpectedReference(SptNP(noun)) => {
      SilExpectedReference(noun)
    }
    case SilExpectedReference(np : SptNP) => {
      analyzer.analyzeNounPhrase(np)
    }
    case SilExpectedReference(noun : ShlurdSyntaxNoun) => {
      SilNounReference(
        analyzer.getWord(noun.child),
        DETERMINER_UNSPECIFIED,
        analyzer.getCount(noun))
    }
    case SilExpectedNounlikeReference(
      syntaxTree, nounlike : ShlurdSyntaxPreTerminal, determiner)
        if (nounlike.isNoun || nounlike.isAdjectival) =>
    {
      // we allow mislabeled adjectives to handle
      // cases like "roll up the blind"
      SilNounReference(
        analyzer.getWord(nounlike.child),
        determiner,
        analyzer.getCount(nounlike))
    }
    case SilExpectedReference(pronoun : ShlurdSyntaxPronoun) => {
      recognizePronounReference(pronoun.child)
    }
  }

  private def replaceExpectedState = replacementMatcher {
    case SilExpectedPrepositionalState(syntaxTree) => {
      analyzer.expectPrepositionalState(syntaxTree)
    }
    case SilExpectedPropertyState(preTerminal : ShlurdSyntaxPreTerminal) => {
      SilPropertyState(analyzer.getWord(preTerminal.child))
    }
    case SilExpectedExistenceState(_) => {
      SilExistenceState()
    }
    case SilExpectedComplementState(adjp : SptADJP) => {
      analyzer.expectPropertyComplementState(adjp.children)
    }
    case SilExpectedComplementState(
      syntaxTree @ (_ : SptADVP | _ : SptPP)) =>
    {
      val seq = syntaxTree.children
      if ((seq.head.isPreposition || seq.head.isAdverb) && (seq.size > 1) &&
        (!seq.exists(_.isPrepositionalPhrase)))
      {
        SilExpectedPrepositionalState(syntaxTree)
      } else {
        analyzer.expectPropertyComplementState(seq)
      }
    }
    case SilExpectedComplementState(vp : SptVP) => {
      // TODO:  ambiguity for action (passive construction) vs
      // state (participial adjective)
      analyzer.expectPropertyComplementState(vp.children)
    }
    case SilExpectedComplementState(syntaxTree : SptPRT) => {
      analyzer.expectPropertyState(requireUnique(syntaxTree.children))
    }
    case SilExpectedComplementState(SptNP(noun)) => {
      analyzer.expectPropertyState(noun)
    }
  }

  private def replaceUnresolvedPredicate = replacementMatcher {
    case predicate : SilUnresolvedStatePredicate
        if (!predicate.state.hasUnresolved) =>
      {
        resolveStatePredicate(predicate)
      }
    case predicate : SilUnresolvedRelationshipPredicate =>
      {
        resolveRelationshipPredicate(predicate)
      }
  }

  private def replaceExpectedSBARQ = replacementMatcher {
    case SilExpectedSentence(sbarq : SptSBARQ, _) => {
      analyzer.analyzeSBARQ(sbarq)
    }
  }

  private def replaceExpectedSQ = replacementMatcher {
    case SilExpectedSentence(sq : SptSQ, forceSQ) => {
      analyzer.analyzeSQ(sq, forceSQ)
    }
  }

  private def replaceAmbiguousSentence = replacementMatcher {
    case ambiguous : SilAmbiguousSentence if (ambiguous.isRipe) => {
      val alternatives = ambiguous.alternatives
      assert(!alternatives.isEmpty)
      val dedup = alternatives.distinct
      if (dedup.size == 1) {
        dedup.head
      } else {
        val clean = dedup.filterNot(_.hasUnknown)
        if (clean.isEmpty) {
          // if all alternatives still contain unknowns, then
          // pick the one with the minimum number of unparsed leaves
          dedup.minBy(_.countUnknownSyntaxLeaves)
        } else {
          if (clean.size == 1) {
            clean.head
          } else {
            SilAmbiguousSentence(clean, true)
          }
        }
      }
    }
  }

  private def replaceUnresolvedWithUnrecognized = replacementMatcher {
    case predicate : SilUnresolvedStatePredicate => {
      resolveStatePredicate(predicate)
    }
    case predicate : SilUnresolvedRelationshipPredicate => {
      resolveRelationshipPredicate(predicate)
    }
  }

  private def resolveRelationshipPredicate(
    predicate : SilUnresolvedRelationshipPredicate) =
  {
    SilRelationshipPredicate(
      predicate.reference,
      predicate.complement,
      predicate.relationship)
  }

  private def resolveStatePredicate(
    predicate : SilUnresolvedStatePredicate) =
  {
    predicate.state match {
      case SilConjunctiveState(DETERMINER_UNSPECIFIED, states, _) => {
        val propertyState = states.head
        val fullySpecifiedState = {
          if (predicate.specifiedState == SilNullState()) {
            if (states.size == 2) {
              states.last
            } else {
              SilConjunctiveState(DETERMINER_ALL, states.tail)
            }
          } else {
            SilConjunctiveState(
              DETERMINER_ALL, Seq(predicate.specifiedState) ++ states.tail)
          }
        }
        val specifiedSubject = analyzer.specifyReference(
          predicate.subject, fullySpecifiedState)
        SilStatePredicate(specifiedSubject, propertyState)
      }
      case _ => {
        val specifiedSubject = analyzer.specifyReference(
          predicate.subject, predicate.specifiedState)
        SilStatePredicate(specifiedSubject, predicate.state)
      }
    }
  }

  private def recognizePronounReference(leaf : ShlurdSyntaxLeaf)
      : SilPronounReference=
  {
    val lemma = leaf.lemma
    val person = lemma match {
      case LEMMA_I | LEMMA_ME | LEMMA_WE | LEMMA_MY |
          LEMMA_OUR | LEMMA_MINE | LEMMA_OURS => PERSON_FIRST
      case LEMMA_YOU | LEMMA_YOUR | LEMMA_YOURS => PERSON_SECOND
      case _ => PERSON_THIRD
    }
    val count = lemma match {
      case LEMMA_WE | LEMMA_US | LEMMA_THEY |
          LEMMA_OUR | LEMMA_THEIR => COUNT_PLURAL
      case _ => COUNT_SINGULAR
    }
    val gender = lemma match {
      case LEMMA_HE | LEMMA_HIM | LEMMA_HIS => GENDER_M
      case LEMMA_SHE | LEMMA_HER | LEMMA_HERS => GENDER_F
      case _ => GENDER_N
    }
    SilPronounReference(person, gender, count)
  }
}