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
package com.lingeringsocket.shlurd.print

import com.lingeringsocket.shlurd.parser._

import org.atteo.evo.inflector.{English => EnglishPluralizer}

import ShlurdEnglishLemmas._

class EnglishSentenceBundle
    extends SilSentenceBundle
{
  override def statePredicateStatement(
    subject : String, verbSeq : Seq[String], state : String,
    modifiers : Seq[String]) =
  {
    if (state.isEmpty) {
      // existential
      compose((verbSeq ++ Seq(subject) ++ modifiers):_*)
    } else {
      composePredicateStatement(subject, verbSeq, state, modifiers)
    }
  }

  override def actionPredicate(
    subject : String,
    verbSeq : Seq[String],
    directObject : Option[String],
    indirectObject : Option[String],
    modifiers : Seq[String],
    mood : SilMood) =
  {
    val complement = compose((directObject.toSeq ++
      indirectObject.map(n => compose(LEMMA_TO, n))):_*)
    if (!mood.isInterrogative || (mood.getModality == MODAL_NEUTRAL)) {
      composePredicateStatement(
        subject, verbSeq, complement, modifiers)
    } else {
      composePredicateQuestion(
        subject, verbSeq, complement, modifiers)
    }
  }

  private def composePredicateStatement(
    subject : String, verbSeq : Seq[String], complement : String,
    modifiers : Seq[String] = Seq.empty) =
  {
    compose((Seq(subject) ++ verbSeq ++ Seq(complement) ++ modifiers):_*)
  }

  override def statePredicateQuestion(
    subject : String, verbSeq : Seq[String], state : String,
    question : Option[SilQuestion],
    modifiers : Seq[String]) =
  {
    if (!question.isEmpty) {
      compose((Seq(subject) ++ verbSeq.take(2).reverse ++
        verbSeq.drop(2) ++ Seq(state) ++ modifiers):_*)
    } else if (state.isEmpty) {
      compose((verbSeq.take(2).reverse ++ verbSeq.drop(2) ++
        Seq(subject) ++ modifiers):_*)
    } else {
      composePredicateQuestion(subject, verbSeq, state, modifiers)
    }
  }

  override def relationshipPredicate(
    subject : String, verbSeq : Seq[String], complement : String,
    relationship : SilRelationship,
    question : Option[SilQuestion],
    mood : SilMood,
    modifiers : Seq[String]) =
  {
    if (mood.isInterrogative && question.isEmpty) {
      relationship match {
        case REL_IDENTITY => {
          composePredicateQuestion(subject, verbSeq, complement, modifiers)
        }
        case REL_ASSOCIATION => {
          if (mood.getModality == MODAL_NEUTRAL) {
            composePredicateStatement(subject, verbSeq, complement, modifiers)
          } else {
            composePredicateQuestion(subject, verbSeq, complement, modifiers)
          }
        }
      }
    } else {
      composePredicateStatement(subject, verbSeq, complement, modifiers)
    }
  }

  private def composePredicateQuestion(
    subject : String, verbSeq : Seq[String], complement : String,
    modifiers : Seq[String] = Seq.empty) =
  {
    val headSeq = Seq(verbSeq.head)
    val tailSeq = verbSeq.drop(1)
    verbSeq.size match {
      // "is Larry clumsy?"
      case 1 =>
        compose((headSeq ++ Seq(subject, complement) ++ modifiers):_*)
      // "is Larry not clumsy?" or "must Larry be clumsy?"
      case 2 =>
        compose((headSeq ++ Seq(subject) ++ tailSeq ++
          Seq(complement) ++ modifiers):_*)
      // "must Larry not be clumsy?"
      case _ =>
        compose((headSeq ++ Seq(subject) ++ tailSeq ++
          Seq(complement) ++ modifiers):_*)
    }
  }

  override def statePredicateCommand(subject : String, state : String,
    modifiers : Seq[String]) =
  {
    compose((Seq(state) ++ Seq(subject) ++ modifiers):_*)
  }

  private def modalCopula(
    mood : SilMood, verbLemma : String,
    person : SilPerson, count : SilCount) =
  {
    val modality = {
      verbLemma match {
        case LEMMA_BE => mood.getModality
        case _ => mood.getModality match {
          case MODAL_NEUTRAL => MODAL_EMPHATIC
          case x => x
        }
      }
    }
    val aux = modality match {
      case MODAL_NEUTRAL => ""
      case MODAL_MUST => LEMMA_MUST
      case MODAL_MAY => LEMMA_MAY
      case MODAL_POSSIBLE => LEMMA_MIGHT
      case MODAL_CAPABLE => LEMMA_CAN
      case MODAL_PERMITTED => LEMMA_MAY
      case MODAL_SHOULD => LEMMA_SHOULD
      case MODAL_EMPHATIC | MODAL_ELLIPTICAL => {
        count match {
          case COUNT_SINGULAR => {
            person match {
              case PERSON_THIRD => LEMMA_DOES
              case _ => LEMMA_DO
            }
          }
          case COUNT_PLURAL => LEMMA_DO
        }
      }
    }
    val prefix = {
      if (mood.isNegative) {
        Seq(aux, LEMMA_NOT)
      } else {
        Seq(aux)
      }
    }
    modality match {
      case MODAL_ELLIPTICAL => prefix
      case _ => prefix :+ verbLemma
    }
  }

  override def delemmatizeVerb(
    person : SilPerson, gender : SilGender, count : SilCount,
    mood : SilMood, isExistential : Boolean,
    verb : SilWord) : Seq[String] =
  {
    val verbLemma = verb.lemma
    if ((verbLemma != LEMMA_BE) && mood.isNegative) {
      return modalCopula(mood, verbLemma, person, count)
    }
    val seq = mood.getModality match {
      case MODAL_NEUTRAL => {
        val inflected = {
          count match {
            case COUNT_SINGULAR => {
              verbLemma match {
                case LEMMA_BE => {
                  person match {
                    case PERSON_FIRST => "am"
                    case PERSON_SECOND => "are"
                    case PERSON_THIRD => "is"
                  }
                }
                case LEMMA_HAVE => {
                  person match {
                    case PERSON_THIRD => "has"
                    case _ => LEMMA_HAVE
                  }
                }
                case LEMMA_EXIST => {
                  person match {
                    case PERSON_THIRD => "exists"
                    case _ => LEMMA_EXIST
                  }
                }
                case _ => delemmatizeWord(verb)
              }
            }
            case COUNT_PLURAL => {
              verbLemma match {
                case LEMMA_BE => "are"
                case _ => delemmatizeWord(verb)
              }
            }
          }
        }
        if (mood.isNegative) {
          Seq(inflected, LEMMA_NOT)
        } else {
          Seq(inflected)
        }
      }
      case _ => {
        modalCopula(mood, verbLemma, person, count)
      }
    }
    if (isExistential) {
      Seq(LEMMA_THERE) ++ seq
    } else {
      seq
    }
  }

  override def adpositionString(adposition : SilAdposition) =
  {
    compose(adposition.words.map(delemmatizeWord):_*)
  }

  override def actionVerb(
    action : SilWord) =
  {
    delemmatizeWord(action)
  }

  override def changeStateVerb(
    state : SilWord, changeVerb : Option[SilWord]) =
  {
    compose(changeVerb.map(_.lemmaUnfolded).getOrElse(""), state.lemmaUnfolded)
  }

  override def delemmatizeNoun(
    noun : SilWord, count : SilCount,
    inflection : SilInflection,
    conjoining : SilConjoining) =
  {
    val unseparated = {
      if (noun.inflected.isEmpty || (inflection == INFLECT_GENITIVE)) {
        val lemma = inflection match {
          case INFLECT_GENITIVE => noun.inflected
          case _ => noun.lemmaUnfolded
        }
        val base = count match {
          case COUNT_SINGULAR => {
            lemma
          }
          case COUNT_PLURAL => {
            EnglishPluralizer.plural(lemma)
          }
        }
        inflection match {
          case INFLECT_GENITIVE => {
            count match {
              case COUNT_SINGULAR => {
                if (base.endsWith("s")) {
                  concat(base, "'")
                } else {
                  concat(base, "'s")
                }
              }
              case COUNT_PLURAL => {
                concat(base, "'")
              }
            }
          }
          case _ => base
        }
      } else {
        noun.inflected
      }
    }
    separate(unseparated, conjoining)
  }

  override def delemmatizeState(
    state : SilWord, mood : SilMood, conjoining : SilConjoining) =
  {
    val unseparated = {
      if (state.inflected.isEmpty) {
        val lemma = state.lemmaUnfolded
        if (lemma.endsWith("ed")) {
          lemma
        } else if (lemma.endsWith("e")) {
          concat(lemma, "d")
        } else {
          concat(lemma, "ed")
        }
      } else {
        state.inflected
      }
    }
    separate(unseparated, conjoining)
  }

  private def delemmatizeWord(word : SilWord) =
  {
    if (word.inflected.isEmpty) {
      word.lemmaUnfolded
    } else {
      word.inflected
    }
  }

  override def delemmatizeQualifier(qualifier : SilWord) =
  {
    delemmatizeWord(qualifier)
  }

  override def conjoin(
    determiner : SilDeterminer,
    separator : SilSeparator,
    inflection : SilInflection,
    items : Seq[String]) =
  {
    val prefix = determiner match {
      case DETERMINER_NONE => LEMMA_NEITHER
      case DETERMINER_UNIQUE => LEMMA_EITHER
      case _ => ""
    }

    val infix = determiner match {
      case DETERMINER_NONE => LEMMA_NOR
      case DETERMINER_ANY | DETERMINER_UNIQUE => LEMMA_OR
      case _ => LEMMA_AND
    }

    val seq = items.dropRight(1).zipWithIndex.flatMap {
      case (n, i) => {
        separator match {
          case SEPARATOR_CONJOINED => Seq(n, infix)
          case _ => {
            if ((i + 2) < items.size) {
              Seq(n)
            } else {
              Seq(n, infix)
            }
          }
        }
      }
    }
    compose((Seq(prefix) ++ seq ++ Seq(items.last)):_*)
  }

  override def conditional(
    antecedent : String,
    consequent : String) =
  {
    compose(LEMMA_IF, concat(antecedent, ","), LEMMA_THEN, consequent)
  }

  override def composeQualifiers(qualifiers : Seq[SilWord]) =
  {
    compose(qualifiers.map(delemmatizeQualifier(_)) :_*)
  }

  override def query(noun : String, question : Option[SilQuestion]) =
  {
    question match {
      case Some(QUESTION_WHICH) => {
        compose(LEMMA_WHICH, noun)
      }
      case Some(QUESTION_WHO) => {
        // FIXME inflection for whom, whose
        compose(LEMMA_WHO)
      }
      case Some(QUESTION_WHAT) => {
        compose(LEMMA_WHAT)
      }
      case Some(QUESTION_HOW_MANY) => {
        compose(LEMMA_HOW, LEMMA_MANY, noun)
      }
      case Some(QUESTION_WHERE) => {
        compose(LEMMA_WHERE)
      }
      case None => noun
    }
  }

  override def qualifiedNoun(qualifiers : String, noun : String) =
  {
    compose(qualifiers, noun)
  }

  override def specifiedNoun(specifier : String, noun : String) =
  {
    compose(noun, specifier)
  }

  override def determinedNoun(determiner : SilDeterminer, noun : String) =
  {
    val determinerString = determiner match {
      case DETERMINER_UNSPECIFIED => ""
      case DETERMINER_NONE => LEMMA_NO
      case DETERMINER_UNIQUE => LEMMA_THE
      case DETERMINER_NONSPECIFIC => {
        // FIXME:  in reality it can be a little more complicated...
        if ("aeiou".contains(noun.head)) {
          "an"
        } else {
          LEMMA_A
        }
      }
      case DETERMINER_ANY => LEMMA_ANY
      case DETERMINER_SOME => LEMMA_SOME
      case DETERMINER_ALL => LEMMA_ALL
      case SilIntegerDeterminer(number : Int) => {
        // FIXME:  render all small numbers
        if (number == 1) {
          LEMMA_ONE
        } else {
          number.toString
        }
      }
    }
    compose(determinerString, noun)
  }

  override def adpositionedNoun(
    position : String, noun : String, conjoining : SilConjoining) =
  {
    separate(compose(position, noun), conjoining)
  }

  override def genitivePhrase(genitive : String, head : String) =
  {
    compose(genitive, head)
  }

  override def pronoun(
    person : SilPerson, gender : SilGender, count : SilCount,
    distance : SilDistance, inflection : SilInflection,
    conjoining : SilConjoining) =
  {
    val unseparated = {
      person match {
        case PERSON_FIRST => count match {
          case COUNT_SINGULAR => inflection match {
            case INFLECT_ACCUSATIVE | INFLECT_DATIVE => LEMMA_ME
            case INFLECT_GENITIVE => LEMMA_MY
            case _ => "I"
          }
          case COUNT_PLURAL => inflection match {
            case INFLECT_ACCUSATIVE | INFLECT_DATIVE => LEMMA_US
            case INFLECT_GENITIVE => LEMMA_OUR
            case _ => LEMMA_WE
          }
        }
        case PERSON_SECOND => inflection match {
          case INFLECT_GENITIVE => LEMMA_YOUR
          case _ => LEMMA_YOU
        }
        case PERSON_THIRD => count match {
          case COUNT_SINGULAR => gender match {
            case GENDER_M => inflection match {
              case INFLECT_ACCUSATIVE | INFLECT_DATIVE => LEMMA_HIM
              case INFLECT_GENITIVE => LEMMA_HIS
              case _ => LEMMA_HE
            }
            case GENDER_F => inflection match {
              case INFLECT_ACCUSATIVE | INFLECT_GENITIVE |
                  INFLECT_DATIVE => LEMMA_HER
              case _ => LEMMA_SHE
            }
            case GENDER_N => distance match {
              case DISTANCE_HERE => LEMMA_THIS
              case DISTANCE_THERE => LEMMA_THAT
              case DISTANCE_UNSPECIFIED => inflection match {
                case INFLECT_GENITIVE => LEMMA_ITS
                case _ => LEMMA_IT
              }
            }
          }
          case COUNT_PLURAL => distance match {
            case DISTANCE_HERE => LEMMA_THESE
            case DISTANCE_THERE => LEMMA_THOSE
            case DISTANCE_UNSPECIFIED => inflection match {
              case INFLECT_ACCUSATIVE | INFLECT_DATIVE => LEMMA_THEM
              case INFLECT_GENITIVE => LEMMA_THEIR
              case _ => LEMMA_THEY
            }
          }
        }
      }
    }
    separate(unseparated, conjoining)
  }

  override def unknownSentence() =
  {
    "blah blah blah"
  }

  override def unknownReference() =
  {
    "something or other"
  }

  override def unknownState() =
  {
    "discombobulated"
  }

  override def unknownVerbModifier() =
  {
    "mimsily"
  }

  override def unknownCopula() =
  {
    LEMMA_BE
  }

  override def unknownPredicateStatement() =
  {
    "foo is bar"
  }

  override def unknownPredicateCommand() =
  {
    "make it so"
  }

  override def unknownPredicateQuestion() =
  {
    "is it what now"
  }

  override def respondToQuery(sentence : String) =
  {
    ShlurdParseUtils.capitalize(sentence)
  }

  override def respondToCounterfactual(sentence : String) =
  {
    compose("But", sentence.stripSuffix("."), "already.")
  }

  override def respondAmbiguous(noun : SilWord) =
  {
    compose("Please be more specific about which",
      noun.lemmaUnfolded, "you mean.")
  }

  override def respondUnknown(word : SilWord) =
  {
    compose("Sorry, I don't know about any",
      concat("'", word.lemmaUnfolded, "'."))
  }

  override def respondUnknownState(subject : String, state : SilWord) =
  {
    compose("Sorry, I don't know what",
      concat("'", state.lemmaUnfolded, "'"),
      "means for", concat(subject, "."))
  }

  override def respondUnknownPronoun(pronoun : String) =
  {
    compose("Sorry, when you say",
      concat("'", pronoun, "'"), "I don't know who or what you mean.")
  }

  override def respondNonexistent(noun : SilWord) =
  {
    compose("But I don't know about any such",
      concat(noun.lemmaUnfolded, "."))
  }

  override def respondCannotUnderstand() =
  {
    "Sorry, I cannot understand what you said."
  }

  override def respondDontKnow() =
  {
    "I don't know."
  }

  override def respondNotUnderstood(
    mood : SilMood, predicate : String, errorPhrase : String) =
  {
    val prefix = mood match {
      case _ : SilIndicativeMood => {
        "I think you are saying"
      }
      case _ : SilInterrogativeMood => {
        "I think you are asking"
      }
      case MOOD_IMPERATIVE => {
        "I think you are telling me to"
      }
    }
    compose(
      prefix,
      concat(predicate, ","),
      "but I can't understand the phrase",
      concat("\"", errorPhrase, "\""))
  }

  override def predicateUnrecognizedSubject(
    mood : SilMood, complement : String, verbSeq : Seq[String],
    count : SilCount, changeVerb : Option[SilWord],
    question : Option[SilQuestion]) =
  {
    val entity = count match {
      case COUNT_SINGULAR => {
        "entity"
      }
      case COUNT_PLURAL => {
        "entities"
      }
    }
    val something = {
      if (question.isEmpty) {
        compose("some", entity)
      } else {
        ""
      }
    }
    mood match {
      case _ : SilIndicativeMood => {
        compose("that",
          composePredicateStatement(something, verbSeq, complement))
      }
      case _ : SilInterrogativeMood => {
        val whord = {
          if (question.isEmpty) {
            "whether"
          } else {
            query(entity, question)
          }
        }
        compose(whord,
          composePredicateStatement(something, verbSeq, complement))
      }
      case MOOD_IMPERATIVE => {
        compose(
          changeVerb.map(_.lemmaUnfolded).getOrElse(""), complement, something)
      }
    }
  }

  override def predicateUnrecognizedComplement(
    mood : SilMood, subject : String,
    verbSeq : Seq[String],
    question : Option[SilQuestion],
    isRelationship : Boolean) =
  {
    mood match {
      case MOOD_IMPERATIVE => {
        compose("do something with", subject)
      }
      case _ => {
        if (question.isEmpty) {
          compose("something about", subject)
        } else {
          val complement = {
            if (isRelationship) {
              compose("some", "entity")
            } else {
              compose("in", "some", "state")
            }
          }
          composePredicateStatement(
            query(subject, question), verbSeq, complement)
        }
      }
    }
  }

  override def respondCompliance() =
  {
    "OK."
  }

  override def affirmAssumption(sentence : String, strength : Boolean) =
  {
    val affirmation = {
      if (strength) {
        "Right"
      } else {
        "Yes"
      }
    }
    if (sentence.isEmpty) {
      concat(affirmation, ".")
    } else {
      compose(concat(affirmation, ","), sentence)
    }
  }

  override def contradictAssumption(sentence : String, strength : Boolean) =
  {
    if (sentence.isEmpty) {
      "No."
    } else {
      if (strength) {
        compose("No, actually", sentence)
      } else {
        compose("No,", sentence)
      }
    }
  }
}
