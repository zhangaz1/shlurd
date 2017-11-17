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
package com.lingeringsocket.shlurd.world

import com.lingeringsocket.shlurd.parser._
import com.lingeringsocket.shlurd.print._

import scala.util._

import org.kiama.rewriting._

import spire.math._

import scala.collection._

case class ShlurdStateChangeInvocation[E<:ShlurdEntity](
  entities : Set[E],
  state : ShlurdWord)
{
}

class ShlurdInterpreter[E<:ShlurdEntity, P<:ShlurdProperty](
  world : ShlurdWorld[E,P])
{
  private val sentencePrinter = new ShlurdSentencePrinter

  class ResultCollector
  {
    val entityMap = new mutable.HashMap[E, Trilean]
    val states = new mutable.HashSet[ShlurdWord]
  }

  def fail(msg : String) = world.fail(msg)

  def interpret(sentence : ShlurdSentence) : String =
  {
    val resultCollector = new ResultCollector
    sentence match {
      case ShlurdStateChangeCommand(predicate, formality) => {
        evaluatePredicate(predicate, resultCollector) match {
          case Success(Trilean.True) => {
            // FIXME:  use proper rephrasing
            "But it already is."
          }
          case Success(_) => {
            assert(resultCollector.states.size == 1)
            executeInvocation(
              ShlurdStateChangeInvocation(
                resultCollector.entityMap.filterNot(
                  _._2.assumeFalse).keySet,
                resultCollector.states.head))
            "Okay, I will get right on that."
          }
          case Failure(e) => {
            diagnostics(e)
            e.getMessage
          }
        }
      }
      case ShlurdPredicateSentence(predicate, mood, formality) => {
        mood match {
          // FIXME deal with positive, modality
          case ShlurdInterrogativeMood(positive, modality) => {
            evaluatePredicate(predicate, resultCollector) match {
              case Success(Trilean.Unknown) => {
                "I don't know."
              }
              case Success(truth) => {
                val truthBoolean = truth.assumeFalse
                val (normalizedResponse, negateCollection) =
                  normalizeResponse(
                    predicate, resultCollector)
                val responseMood = ShlurdIndicativeMood(
                  truthBoolean || negateCollection)
                sentencePrinter.sb.respondToAssumption(
                  ASSUMED_TRUE, truthBoolean,
                  sentencePrinter.print(
                    ShlurdPredicateSentence(
                      normalizedResponse,
                      responseMood)),
                  false)
              }
              case Failure(e) => {
                diagnostics(e)
                e.getMessage
              }
            }
          }
          case _ : ShlurdIndicativeMood => {
            // FIXME deal with mood.getModality
            val positivity = mood.isPositive
            val predicateTruth = evaluatePredicate(predicate, resultCollector)
            val responseMood = {
              predicateTruth match {
                case Success(Trilean.False) => {
                  MOOD_INDICATIVE_NEGATIVE
                }
                case _ => {
                  // FIXME:  deal with uncertainty
                  MOOD_INDICATIVE_POSITIVE
                }
              }
            }
            predicateTruth match {
              case Success(Trilean.Unknown) => {
                // FIXME:  maybe try to update state?
                "Oh, really?  Thanks for letting me know."
              }
              case Success(truth) => {
                if (truth.assumeFalse == positivity) {
                  val (normalizedResponse, negateCollection) =
                    normalizeResponse(predicate, resultCollector)
                  assert(!negateCollection)
                  sentencePrinter.sb.respondToAssumption(
                    ASSUMED_TRUE, true,
                    sentencePrinter.print(
                      ShlurdPredicateSentence(
                        normalizedResponse,
                        responseMood)),
                    true)
                } else {
                  // FIXME:  add details on inconsistency, and maybe try
                  // to update state?
                  "Oh, really?"
                }
              }
              case Failure(e) => {
                // FIXME:  try to update state?
                diagnostics(e)
                e.getMessage
              }
            }
          }
          case _ => {
            "I am not sure what you mean by that."
          }
        }
      }
      case ShlurdUnknownSentence => {
        "Sorry, I do not understand."
      }
    }
  }

  private def diagnostics(t : Throwable)
  {
    if (false) {
      t.printStackTrace
    }
  }

  protected def executeInvocation(
    invocation : ShlurdStateChangeInvocation[E])
  {
  }

  private def evaluateDeterminer(
    tries : Iterable[Try[Trilean]], determiner : ShlurdDeterminer)
      : Try[Trilean] =
  {
    tries.find(_.isFailure) match {
      // FIXME:  combine failures
      case Some(failed) => failed
      case _ => {
        val results = tries.map(_.get)
        determiner match {
          case DETERMINER_NONE => {
            Success(!results.fold(Trilean.False)(_|_))
          }
          case DETERMINER_UNIQUE => {
            val lowerBound = results.count(_.assumeFalse)
            if (lowerBound > 1) {
              Success(Trilean.False)
            } else {
              if (results.exists(_.isUnknown)) {
                Success(Trilean.Unknown)
              } else {
                Success(Trilean(lowerBound == 1))
              }
            }
          }
          case DETERMINER_ALL => {
            Success(results.fold(Trilean.True)(_&_))
          }
          case DETERMINER_ANY | DETERMINER_SOME | DETERMINER_NONSPECIFIC => {
            Success(results.fold(Trilean.False)(_|_))
          }
          case _ => fail("I don't know about this determiner")
        }
      }
    }
  }

  private def evaluatePredicate(
    predicate : ShlurdPredicate,
    resultCollector : ResultCollector) : Try[Trilean] =
  {
    predicate match {
      case ShlurdStatePredicate(subject, state) => {
        state match {
          case ShlurdConjunctiveState(determiner, states, _) => {
            // FIXME:  how to write to resultCollector.entityMap in this case?
            val tries = states.map(
              s => evaluatePredicate(
                ShlurdStatePredicate(subject, s), resultCollector))
            evaluateDeterminer(tries, determiner)
          }
          case ShlurdExistenceState() => {
            evaluateExistencePredicate(subject, resultCollector)
          }
          case ShlurdPropertyState(word) => {
            evaluatePropertyStatePredicate(subject, word, resultCollector)
          }
          case ShlurdLocationState(locative, location) => {
            evaluateLocationStatePredicate(
              subject, locative, location, resultCollector)
          }
          case ShlurdUnknownState => fail(
            "I don't know about this kind of state")
        }
      }
      case _ => fail(
        "I don't know about this kind of predicate")
    }
  }

  private def evaluatePredicateOverReference(
    reference : ShlurdReference,
    context : ShlurdReferenceContext,
    resultCollector : ResultCollector,
    qualifiers : Seq[ShlurdWord] = Seq.empty
  )(evaluator : E => Try[Trilean])
      : Try[Trilean] =
  {
    reference match {
      case ShlurdEntityReference(word, determiner, count) => {
        val lemma = word.lemma
        world.resolveEntity(
          lemma, context, world.qualifierSet(qualifiers)) match
        {
          case Success(entities) => {
            determiner match {
              case DETERMINER_UNIQUE => {
                if (entities.isEmpty) {
                  fail("I don't know about any such " + lemma)
                } else {
                  count match {
                    case COUNT_SINGULAR => {
                      if (entities.size > 1) {
                        fail("I am not sure which " + lemma + " you mean")
                      } else {
                        invokeEvaluator(
                          entities.head, resultCollector, evaluator)
                      }
                    }
                    case COUNT_PLURAL => {
                      if (entities.size > 1) {
                        evaluateDeterminer(
                          entities.map(
                            invokeEvaluator(_, resultCollector, evaluator)),
                          DETERMINER_ALL)
                      } else {
                        fail("I know about only one " + lemma)
                      }
                    }
                  }
                }
              }
              case _ => {
                evaluateDeterminer(
                  entities.map(invokeEvaluator(_, resultCollector, evaluator)),
                  determiner)
              }
            }
          }
          case Failure(e) => Failure(e)
        }
      }
      case ShlurdPronounReference(person, gender, count, _) => {
        // also prevent qualifiers here
        fail("FIXME")
      }
      case ShlurdConjunctiveReference(determiner, references, separator) => {
        val results = references.map(
          evaluatePredicateOverReference(
            _, context, resultCollector, qualifiers)(evaluator))
        evaluateDeterminer(results, determiner)
      }
      case ShlurdQualifiedReference(sub, qualifiers) => {
        evaluatePredicateOverReference(
          sub, context, resultCollector, qualifiers)(evaluator)
      }
      case ShlurdGenitiveReference(genitive, reference) => {
        // maybe allow qualifiers here in case the parser gets
        // it wrong (e.g. the red shadow of the moon)
        fail("FIXME")
      }
      case ShlurdUnknownReference => {
        fail("I don't know about this kind of reference")
      }
    }
  }

  private def invokeEvaluator(
    entity : E,
    resultCollector : ResultCollector,
    evaluator : E => Try[Trilean]) : Try[Trilean] =
  {
    val result = evaluator(entity)
    result.foreach(resultCollector.entityMap.put(entity, _))
    result
  }

  private def evaluateExistencePredicate(
    subjectRef : ShlurdReference, resultCollector : ResultCollector)
      : Try[Trilean] =
  {
    evaluatePredicateOverReference(subjectRef, REF_SUBJECT, resultCollector) {
      entity => Success(Trilean.True)
    }
  }

  private def evaluatePropertyStatePredicate(
    subjectRef : ShlurdReference,
    state : ShlurdWord,
    resultCollector : ResultCollector)
      : Try[Trilean] =
  {
    evaluatePredicateOverReference(subjectRef, REF_SUBJECT, resultCollector)
    {
      entity => {
        world.resolveProperty(entity, state.lemma) match {
          case Success(property) => {
            resultCollector.states += ShlurdWord(
              property.getStates()(state.lemma), state.lemma)
            world.evaluateEntityPropertyPredicate(
              entity, property, state.lemma)
          }
          case Failure(e) => Failure(e)
        }
      }
    }
  }

  private def evaluateLocationStatePredicate(
    subjectRef : ShlurdReference, locative : ShlurdLocative,
    locationRef : ShlurdReference,
    resultCollector : ResultCollector)
      : Try[Trilean] =
  {
    evaluatePredicateOverReference(
      subjectRef, REF_LOCATED, resultCollector)
    {
      subjectEntity => {
        evaluatePredicateOverReference(
          locationRef, REF_LOCATION, resultCollector)
        {
          locationEntity => {
            world.evaluateEntityLocationPredicate(
              subjectEntity, locationEntity, locative)
          }
        }
      }
    }
  }

  // FIXME:  cutoff for maximum enumeration size before switching
  // to summary form
  private def normalizeResponse(
    predicate : ShlurdPredicate,
    resultCollector : ResultCollector)
      : (ShlurdPredicate, Boolean) =
  {
    var negateCollection = false
    val entityDeterminer = predicate match {
      case ShlurdStatePredicate(subject, ShlurdExistenceState()) => {
        DETERMINER_NONSPECIFIC
      }
      case _ => {
        DETERMINER_UNIQUE
      }
    }
    def normalizeConjunctionWrapper(
      separator : ShlurdSeparator,
      allRef : => ShlurdReference) =
    {
      normalizeConjunction(
        resultCollector, entityDeterminer, separator
      ) match {
        case Some(nc : ShlurdConjunctiveReference) => {
          negateCollection = true
          nc
        }
        case Some(nr) => {
          nr
        }
        case _ => {
          allRef
        }
      }
    }
    val rewriteDeterminers =
      Rewriter.rule[ShlurdPhrase] {
        case ShlurdEntityReference(
          entity, DETERMINER_ANY | DETERMINER_SOME, count
        ) => {
          normalizeDisjunction(
            resultCollector, entityDeterminer, SEPARATOR_OXFORD_COMMA).getOrElse
          {
            negateCollection = true
            ShlurdEntityReference(entity, DETERMINER_NONE, count)
          }
        }
        case ShlurdEntityReference(
          entity, DETERMINER_ALL, count
        ) => {
          normalizeConjunctionWrapper(
            SEPARATOR_OXFORD_COMMA,
            ShlurdEntityReference(entity, DETERMINER_ALL, count))
        }
        case ShlurdConjunctiveReference(determiner, references, separator) => {
          determiner match {
            case DETERMINER_ANY => {
              normalizeDisjunction(
                resultCollector, entityDeterminer, separator).getOrElse
              {
                negateCollection = true
                ShlurdConjunctiveReference(
                  DETERMINER_NONE, references, separator)
              }
            }
            case DETERMINER_ALL => {
              normalizeConjunctionWrapper(
                separator,
                ShlurdConjunctiveReference(
                  DETERMINER_ALL, references, separator))
            }
            case _ => {
              ShlurdConjunctiveReference(determiner, references, separator)
            }
          }
        }
      }
    val rewritten = Rewriter.rewrite(
      Rewriter.everywhere("rewriteDeterminers", rewriteDeterminers))(predicate)
    (rewritten, negateCollection)
  }

  private def normalizeDisjunction(
    resultCollector : ResultCollector, entityDeterminer : ShlurdDeterminer,
    separator : ShlurdSeparator) : Option[ShlurdReference] =
  {
    val trueEntities = resultCollector.entityMap.filter(
      _._2.assumeFalse).keySet
    if (trueEntities.isEmpty) {
      None
    } else if (trueEntities.size == 1) {
      Some(world.specificReference(trueEntities.head, entityDeterminer))
    } else {
      Some(ShlurdConjunctiveReference(
        DETERMINER_ALL,
        trueEntities.map(
          world.specificReference(_, entityDeterminer)).toSeq,
        separator))
    }
  }

  private def normalizeConjunction(
    resultCollector : ResultCollector, entityDeterminer : ShlurdDeterminer,
    separator : ShlurdSeparator) : Option[ShlurdReference] =
  {
    val falseEntities = resultCollector.entityMap.filterNot(
      _._2.assumeTrue).keySet
    if (falseEntities.isEmpty) {
      None
    } else if (falseEntities.size == 1) {
      Some(world.specificReference(falseEntities.head, entityDeterminer))
    } else {
      Some(ShlurdConjunctiveReference(
        DETERMINER_NONE,
        falseEntities.map(
          world.specificReference(_, entityDeterminer)).toSeq,
        separator))
    }
  }
}
