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
package com.lingeringsocket.shlurd.print

import com.lingeringsocket.shlurd.parser._

class ShlurdSentencePrinter(parlance : ShlurdParlance = ShlurdDefaultParlance)
{
  val sb = ShlurdSentenceBundle(parlance)

  def print(sentence : ShlurdSentence) : String =
  {
    sentence match {
      case ShlurdPredicateSentence(predicate, mood, formality) => {
        mood match {
          case _ : ShlurdIndicativeMood =>  {
            sb.terminatedSentence(
              printPredicateStatement(predicate, mood), mood, formality)
          }
          case _ : ShlurdInterrogativeMood => {
            sb.terminatedSentence(
              printPredicateQuestion(predicate, mood), mood, sentence.formality)
          }
          case MOOD_IMPERATIVE => {
            sb.terminatedSentence(
              printPredicateCommand(predicate), mood, sentence.formality)
          }
        }
      }
      case ShlurdStateChangeCommand(predicate, formality) => {
        sb.terminatedSentence(
          printPredicateCommand(predicate), sentence.mood, formality)
      }
      case ShlurdUnknownSentence => {
        sb.unknownSentence
      }
    }
  }

  def print(
    reference : ShlurdReference,
    inflection : ShlurdInflection,
    conjoining : ShlurdConjoining) : String =
  {
    reference match {
      case ShlurdEntityReference(entity, determiner, count) => {
        sb.determinedNoun(
          determiner,
          sb.delemmatizeNoun(entity, count, inflection, conjoining))
      }
      case ShlurdPronounReference(person, gender, count) => {
        sb.pronoun(person, gender, count, inflection, conjoining)
      }
      case ShlurdConjunctiveReference(determiner, references, separator) => {
        sb.conjoin(
          determiner, separator, inflection,
          references.zipWithIndex.map {
            case (r, i) => print(
              r, inflection,
              ShlurdConjoining(determiner, separator, i, references.size))
          }
        )
      }
      case ShlurdQualifiedReference(sub, qualifiers) => {
        val qualifierString = sb.composeQualifiers(qualifiers)
        sub match {
          case ShlurdEntityReference(entity, determiner, count) => {
            sb.determinedNoun(
              determiner,
              sb.qualifiedNoun(
                qualifierString,
                sb.delemmatizeNoun(entity, count, inflection, conjoining)))
          }
          case ShlurdPronounReference(person, gender, count) => {
            // kludged representation for "three of them"
            sb.locationalNoun(
              sb.qualifiedNoun(qualifierString, "of"),
              sb.pronoun(
                person, gender, count, INFLECT_ACCUSATIVE,
                ShlurdConjoining.NONE),
              conjoining)
          }
          case _ => {
            sb.qualifiedNoun(
              qualifierString, print(sub, inflection, conjoining))
          }
        }
      }
      case ShlurdGenitiveReference(genitive, reference) => {
        val qualifierString = genitive match {
          case ShlurdPronounReference(person, gender, count) => {
            sb.pronoun(
              person, gender, count, INFLECT_GENITIVE, ShlurdConjoining.NONE)
          }
          case _ => {
            print(genitive, INFLECT_GENITIVE, ShlurdConjoining.NONE)
          }
        }
        sb.genitivePhrase(
          qualifierString, print(reference, inflection, conjoining))
      }
      case ShlurdUnknownReference => {
        sb.unknownReference
      }
    }
  }

  def print(
    state : ShlurdState, mood : ShlurdMood, conjoining : ShlurdConjoining)
      : String =
  {
    state match {
      case ShlurdExistenceState() => {
        ""
      }
      case ShlurdPropertyState(state) => {
        sb.delemmatizeState(state, mood, conjoining)
      }
      case ShlurdLocationState(locative, location) => {
        sb.locationalNoun(
          sb.position(locative),
          print(location, INFLECT_NONE, ShlurdConjoining.NONE),
          conjoining)
      }
      case ShlurdConjunctiveState(determiner, states, separator) => {
        sb.conjoin(
          determiner, separator, INFLECT_NONE,
          states.zipWithIndex.map {
            case (s, i) => print(
              s, mood,
              ShlurdConjoining(determiner, separator, i, states.size))
          }
        )
      }
      case ShlurdUnknownState => {
        sb.unknownState
      }
    }
  }

  def printChangeStateVerb(state : ShlurdState) =
  {
    state match {
      case ShlurdPropertyState(state) => {
        sb.changeStateVerb(state)
      }
      // FIXME:  conjoining, e.g. "close and lock the door"
      case _ => print(state, MOOD_IMPERATIVE, ShlurdConjoining.NONE)
    }
  }

  def printPredicateStatement(
    predicate : ShlurdPredicate, mood : ShlurdMood) : String =
  {
    predicate match {
      case ShlurdStatePredicate(subject, state) => {
        sb.statePredicateStatement(
          print(subject, INFLECT_NOMINATIVE, ShlurdConjoining.NONE),
          getCopula(subject, state, mood),
          print(state, mood, ShlurdConjoining.NONE))
      }
      case ShlurdIdentityPredicate(subject, complement) => {
        sb.identityPredicateStatement(
          print(subject, INFLECT_NOMINATIVE, ShlurdConjoining.NONE),
          getCopula(subject, ShlurdExistenceState(), mood),
          print(complement, INFLECT_NOMINATIVE, ShlurdConjoining.NONE))
      }
      case ShlurdUnknownPredicate => {
        sb.unknownPredicateStatement
      }
    }
  }

  def printPredicateCommand(predicate : ShlurdPredicate) =
  {
    predicate match {
      case ShlurdStatePredicate(subject, state) => {
        sb.statePredicateCommand(
          print(subject, INFLECT_ACCUSATIVE, ShlurdConjoining.NONE),
          printChangeStateVerb(state))
      }
      case _ => {
        sb.unknownPredicateCommand
      }
    }
  }

  def printPredicateQuestion(
    predicate : ShlurdPredicate, mood : ShlurdMood) : String =
  {
    predicate match {
      case ShlurdStatePredicate(subject, state) => {
        sb.statePredicateQuestion(
          print(subject, INFLECT_NOMINATIVE, ShlurdConjoining.NONE),
          getCopula(subject, state, mood),
          print(state, mood, ShlurdConjoining.NONE))
      }
      case ShlurdIdentityPredicate(subject, complement) => {
        sb.identityPredicateQuestion(
          print(subject, INFLECT_NOMINATIVE, ShlurdConjoining.NONE),
          getCopula(subject, ShlurdExistenceState(), mood),
          print(complement, INFLECT_NOMINATIVE, ShlurdConjoining.NONE))
      }
      case ShlurdUnknownPredicate => {
        sb.unknownPredicateQuestion
      }
    }
  }

  def getCopula(
    subject : ShlurdReference, state : ShlurdState, mood : ShlurdMood)
      : Seq[String] =
  {
    val isExistential = state match {
      case ShlurdExistenceState() => true
      case _ => false
    }
    subject match {
      case ShlurdPronounReference(person, gender, count) => {
        sb.copula(person, gender, count, mood, isExistential)
      }
      case ShlurdEntityReference(entity, determiner, count) => {
        sb.copula(PERSON_THIRD, GENDER_N, count, mood, isExistential)
      }
      case ShlurdConjunctiveReference(determiner, references, _) => {
        val count = if (isExistential) {
          // FIXME:  this is probably English-specific
          getCount(references.head)
        } else {
          determiner match {
            case DETERMINER_ALL => COUNT_PLURAL
            // DETERMINER_NONE is debatable
            case _ => COUNT_SINGULAR
          }
        }
        // FIXME:  also derive person and gender from underlying references,
        // since it makes a difference in languages such as Spanish
        sb.copula(PERSON_THIRD, GENDER_N, count, mood, isExistential)
      }
      case ShlurdQualifiedReference(reference, qualifiers) => {
        getCopula(reference, state, mood)
      }
      case ShlurdGenitiveReference(genitive, reference) => {
        getCopula(reference, state, mood)
      }
      case ShlurdUnknownReference => {
        Seq(sb.unknownCopula)
      }
    }
  }

  private def getCount(reference : ShlurdReference) : ShlurdCount =
  {
    reference match {
      case ShlurdPronounReference(person, gender, count) =>
        count
      case ShlurdEntityReference(entity, determiner, count) =>
        count
      case ShlurdConjunctiveReference(determiner, references, _) => {
        determiner match {
          case DETERMINER_ALL => COUNT_PLURAL
          // DETERMINER_NONE is debatable
          case _ => COUNT_SINGULAR
        }
      }
      case ShlurdQualifiedReference(reference, qualifiers) =>
        getCount(reference)
      case ShlurdGenitiveReference(genitive, reference) =>
        getCount(reference)
      case ShlurdUnknownReference => COUNT_SINGULAR
    }
  }
}

case class ShlurdSentenceUnprintable() extends RuntimeException
{
}
