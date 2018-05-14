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
package com.lingeringsocket.shlurd.world

import com.lingeringsocket.shlurd.parser._
import com.lingeringsocket.shlurd.print._

import scala.util._

import org.kiama.rewriting._

import spire.math._

import scala.collection._

import org.slf4j._

case class ShlurdStateChangeInvocation[E<:ShlurdEntity](
  entities : Set[E],
  state : ShlurdWord)
{
}

case class ShlurdInterpreterParams(
  listLimit : Int = 3
)
{
  def neverSummarize = (listLimit == Int.MaxValue)

  def alwaysSummarize = (listLimit == 0)
}

class ResultCollector[E<:ShlurdEntity]
{
  val entityMap = new mutable.LinkedHashMap[E, Trilean]
  val states = new mutable.LinkedHashSet[ShlurdWord]
}

class ShlurdInterpreter[E<:ShlurdEntity, P<:ShlurdProperty](
  world : ShlurdWorld[E,P],
  generalParams : ShlurdInterpreterParams = ShlurdInterpreterParams())
{
  private val logger = LoggerFactory.getLogger(classOf[ShlurdInterpreter[E,P]])

  private lazy val debugEnabled = logger.isDebugEnabled

  private var debugDepth = 0

  private val sentencePrinter = new ShlurdSentencePrinter

  private val responseRewrite = new ShlurdResponseRewrite(world)

  def fail(msg : String) = world.fail(msg)

  @inline private final def debug(msg : => String)
  {
    if (debugEnabled) {
      val prefix = "*" * debugDepth
      logger.debug(prefix + msg)
    }
  }

  private final def debug(msg : => String, t : Throwable)
  {
    if (debugEnabled) {
      val prefix = "*" * debugDepth
      logger.error(prefix + msg, t)
    }
  }

  def interpret(sentence : ShlurdSentence) : String =
  {
    debug(s"INTERPRETER INPUT : $sentence")
    val response = interpretImpl(sentence)
    debug(s"INTERPRETER RESPONSE : $response")
    response
  }

  private def interpretImpl(sentence : ShlurdSentence) : String =
  {
    if (sentence.hasUnknown) {
      return respondToUnrecognized(sentence)
    }
    val resultCollector = new ResultCollector[E]
    sentence match {
      case ShlurdStateChangeCommand(predicate, _, formality) => {
        debug("STATE CHANGE COMMAND")
        evaluatePredicate(predicate, resultCollector) match {
          case Success(Trilean.True) => {
            debug("COUNTERFACTUAL")
            val (normalizedResponse, negateCollection) =
              responseRewrite.normalizeResponse(
                predicate, resultCollector, generalParams)
            assert(!negateCollection)
            val responseMood = MOOD_INDICATIVE_POSITIVE
            sentencePrinter.sb.respondToCounterfactual(
              sentencePrinter.print(
                ShlurdPredicateSentence(
                  normalizedResponse,
                  responseMood)))
          }
          case Success(_) => {
            assert(resultCollector.states.size == 1)
            val invocation =
              ShlurdStateChangeInvocation(
                resultCollector.entityMap.filterNot(
                  _._2.assumeFalse).keySet,
                resultCollector.states.head)
            debug(s"EXECUTE INVOCATION : $invocation")
            executeInvocation(invocation)
            sentencePrinter.sb.respondCompliance()
          }
          case Failure(e) => {
            debug("ERROR", e)
            e.getMessage
          }
        }
      }
      case ShlurdPredicateQuery(predicate, question, mood, formality) => {
        debug("PREDICATE QUERY")
        // FIXME deal with positive, modality

        val rewrittenPredicate = rewriteQuery(predicate)

        debug(s"REWRITTEN PREDICATE : $rewrittenPredicate")
        evaluatePredicate(rewrittenPredicate, resultCollector) match {
          case Success(Trilean.Unknown) => {
            debug("ANSWER UNKNOWN")
            sentencePrinter.sb.respondDontKnow()
          }
          case Success(truth) => {
            debug(s"ANSWER : $truth")
            val truthBoolean = truth.assumeFalse
            val extremeLimit = question match {
              case QUESTION_WHICH | QUESTION_WHO => Int.MaxValue
              case QUESTION_HOW_MANY => 0
            }
            val (normalizedResponse, negateCollection) =
              responseRewrite.normalizeResponse(
                rewrittenPredicate, resultCollector,
                generalParams.copy(listLimit = extremeLimit))
            debug(s"NORMALIZED RESPONSE : $normalizedResponse")
            val responseMood = ShlurdIndicativeMood(
              truthBoolean || negateCollection)
            sentencePrinter.sb.respondToQuery(
              sentencePrinter.print(
                ShlurdPredicateSentence(
                  normalizedResponse,
                  responseMood)))
          }
          case Failure(e) => {
            debug("ERROR", e)
            e.getMessage
          }
        }
      }
      case ShlurdPredicateSentence(predicate, mood, formality) => {
        mood match {
          // FIXME deal with positive, modality
          case ShlurdInterrogativeMood(positive, modality) => {
            debug("PREDICATE QUERY SENTENCE")
            val query = predicate
            evaluatePredicate(query, resultCollector) match {
              case Success(Trilean.Unknown) => {
                debug("ANSWER UNKNOWN")
                sentencePrinter.sb.respondDontKnow()
              }
              case Success(truth) => {
                debug(s"ANSWER : $truth")
                val truthBoolean = truth.assumeFalse
                val params = query match {
                  case rp : ShlurdRelationshipPredicate => {
                    generalParams.copy(listLimit = 0)
                  }
                  case _ => generalParams
                }
                val (normalizedResponse, negateCollection) =
                  responseRewrite.normalizeResponse(
                    query, resultCollector, params)
                debug(s"NORMALIZED RESPONSE : $normalizedResponse")
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
                debug("ERROR", e)
                e.getMessage
              }
            }
          }
          case _ : ShlurdIndicativeMood => {
            // FIXME deal with mood.getModality
            val positivity = mood.isPositive
            debug(s"POSITIVITY : $positivity")
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
                debug("TRUTH UNKNOWN")
                // FIXME:  maybe try to update state?
                "Oh, really?  Thanks for letting me know."
              }
              case Success(truth) => {
                debug(s"KNOWN TRUTH : $truth")
                if (truth.assumeFalse == positivity) {
                  val (normalizedResponse, negateCollection) =
                    responseRewrite.normalizeResponse(
                      predicate, resultCollector, generalParams)
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
                debug("ERROR", e)
                e.getMessage
              }
            }
          }
          case _ => {
            debug(s"UNEXPECTED MOOD : $mood")
            sentencePrinter.sb.respondCannotUnderstand()
          }
        }
      }
      case ShlurdAmbiguousSentence(alternatives, _) => {
        debug("AMBIGUOUS SENTENCE")
        // FIXME:  try each in turn and use first
        // that does not result in an error
        sentencePrinter.sb.respondCannotUnderstand()
      }
      case _ : ShlurdUnknownSentence => {
        debug("UNKNOWN SENTENCE")
        sentencePrinter.sb.respondCannotUnderstand()
      }
    }
  }

  private def respondToUnrecognized(sentence : ShlurdSentence) : String =
  {
    val sb = sentencePrinter.sb
    assert(sentence.hasUnknown)
    sentence match {
      case ShlurdPredicateSentence(predicate, mood, _) => {
        predicate match {
          case ShlurdStatePredicate(subject, state) => {
            val count = computeMaxCount(
              subject,
              predicate.getInflectedCount)
            val response = respondToUnresolvedPredicate(
              subject, state, mood, count, None)
            if (!response.isEmpty) {
              return response
            }
          }
          case ShlurdRelationshipPredicate(subject, complement, rel) => {
            val count = findKnownCount(subject, complement)
            val response = respondToUnresolvedPredicate(
              subject, complement, mood, count, Some(rel))
            if (!response.isEmpty) {
              return response
            }
          }
          case _ =>
        }
      }
      case ShlurdStateChangeCommand(
        predicate : ShlurdStatePredicate, changeVerb, _) =>
      {
        val count = computeMaxCount(
          predicate.subject,
          predicate.getInflectedCount)
        val response = respondToUnresolvedPredicate(
          predicate.subject, predicate.state,
          MOOD_IMPERATIVE, count, None, changeVerb)
        if (!response.isEmpty) {
          return response
        }
      }
      case ShlurdPredicateQuery(predicate, question, mood, _) => {
        predicate match {
          case ShlurdStatePredicate(subject, state) => {
            val count = computeMaxCount(
              subject,
              predicate.getInflectedCount)
            val response = respondToUnresolvedPredicate(
              subject, state, mood, count, None, None, Some(question))
            if (!response.isEmpty) {
              return response
            }
          }
          case ShlurdRelationshipPredicate(subject, complement, rel) => {
            val count = findKnownCount(subject, complement)
            val response = respondToUnresolvedPredicate(
              subject, complement, mood, count,
              Some(rel), None, Some(question))
            if (!response.isEmpty) {
              return response
            }
          }
          case _ =>
        }
      }
      case _ : ShlurdStateChangeCommand => ;
      case _ : ShlurdAmbiguousSentence => ;
      case _ : ShlurdUnknownSentence => ;
    }
    sb.respondCannotUnderstand()
  }

  private def findKnownCount(
    ref1 : ShlurdReference, ref2 : ShlurdReference) : ShlurdCount =
  {
    if (ref1.hasUnknown) {
      ShlurdReference.getCount(ref2)
    } else {
      ShlurdReference.getCount(ref1)
    }
  }

  private def computeMaxCount(
    ref : ShlurdReference, count : ShlurdCount) : ShlurdCount =
  {
    if (ShlurdReference.getCount(ref) == COUNT_PLURAL) {
      COUNT_PLURAL
    } else if (count == COUNT_PLURAL) {
      COUNT_PLURAL
    } else {
      COUNT_SINGULAR
    }
  }

  private def respondToUnresolvedPredicate(
    subject : ShlurdReference,
    complement : ShlurdPhrase,
    mood : ShlurdMood,
    count : ShlurdCount,
    rel : Option[ShlurdRelationship],
    changeVerb : Option[ShlurdWord] = None,
    question : Option[ShlurdQuestion] = None) : String =
  {
    val sb = sentencePrinter.sb
    val copula = sb.copula(
      PERSON_THIRD, GENDER_N, count,
      mood, false, rel.getOrElse(REL_IDENTITY))
    if (!subject.hasUnknown) {
      assert(complement.hasUnknown)
      sb.respondNotUnderstood(
        mood,
        sb.predicateUnrecognizedComplement(
          mood, subject.toWordString, copula, question, !rel.isEmpty),
        complement.toWordString)
    } else if (!complement.hasUnknown) {
      assert(subject.hasUnknown)
      sb.respondNotUnderstood(
        mood,
        sb.predicateUnrecognizedSubject(
          mood, complement.toWordString, copula, count, changeVerb, question),
        subject.toWordString)
    } else {
      ""
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
    debug(s"EVALUATE DETERMINER : $determiner OVER $tries")
    tries.find(_.isFailure) match {
      // FIXME:  combine failures
      case Some(failed) => failed
      case _ => {
        val results = tries.map(_.get)
        determiner match {
          case DETERMINER_NONE => {
            Success(!results.fold(Trilean.False)(_|_))
          }
          case DETERMINER_UNIQUE | DETERMINER_UNSPECIFIED => {
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
          case _ => fail(sentencePrinter.sb.respondCannotUnderstand())
        }
      }
    }
  }

  private def evaluatePredicate(
    predicate : ShlurdPredicate,
    resultCollector : ResultCollector[E]) : Try[Trilean] =
  {
    debug(s"EVALUATE PREDICATE : $predicate")
    debugDepth += 1
    val result = predicate match {
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
          case ShlurdNullState() | _ : ShlurdUnknownState => {
            debug(s"UNEXPECTED STATE : $state")
            fail(sentencePrinter.sb.respondCannotUnderstand())
          }
        }
      }
      case ShlurdRelationshipPredicate(
        subjectRef, complementRef, relationship) =>
      {
        val subjectCollector = relationship match {
          case REL_IDENTITY => resultCollector
          case REL_ASSOCIATION => new ResultCollector[E]
        }
        val complementCollector = relationship match {
          case REL_IDENTITY => new ResultCollector[E]
          case REL_ASSOCIATION => resultCollector
        }
        evaluatePredicateOverReference(
          subjectRef, REF_SUBJECT, subjectCollector)
        {
          subjectEntity => {
            evaluatePredicateOverReference(
              complementRef, REF_SUBJECT, complementCollector)
            {
              complementEntity => {
                relationship match {
                  case REL_IDENTITY => {
                    val result = Success(
                      Trilean(subjectEntity == complementEntity))
                    debug("RESULT FOR " +
                      s"$subjectEntity == $complementEntity is $result")
                    result
                  }
                  case REL_ASSOCIATION => {
                    // FIXME:  do something less hacky
                    val qualifiers : Set[String] = complementRef match {
                      case ShlurdEntityReference(word, determiner, count) => {
                        Set(word.lemma)
                      }
                      case _ => Set.empty
                    }
                    val result = world.evaluateEntityLocationPredicate(
                      complementEntity, subjectEntity,
                      LOC_GENITIVE_OF, qualifiers)
                    debug("RESULT FOR " +
                      s"$complementEntity LOC_GENITIVE_OF " +
                      s"$subjectEntity with $qualifiers is $result")
                    result
                  }
                }
              }
            }
          }
        }
      }
      case _ => {
        debug("UNEXPECTED PREDICATE TYPE")
        fail(sentencePrinter.sb.respondCannotUnderstand())
      }
    }
    debugDepth -= 1
    debug(s"PREDICATE TRUTH : $result")
    result
  }

  private def evaluatePredicateOverReference(
    reference : ShlurdReference,
    context : ShlurdReferenceContext,
    resultCollector : ResultCollector[E],
    specifiedState : ShlurdState = ShlurdNullState()
  )(evaluator : E => Try[Trilean])
      : Try[Trilean] =
  {
    debug("EVALUATE PREDICATE OVER REFERENCE : " +
      reference + " WITH CONTEXT " + context + " AND SPECIFIED STATE "
      + specifiedState)
    debugDepth += 1
    val result = evaluatePredicateOverReferenceImpl(
      reference, context, resultCollector,
      specifiedState, evaluator)
    debugDepth -= 1
    debug(s"PREDICATE TRUTH OVER REFERENCE : $result")
    result
  }

  private def evaluatePredicateOverReferenceImpl(
    reference : ShlurdReference,
    context : ShlurdReferenceContext,
    resultCollector : ResultCollector[E],
    specifiedState : ShlurdState,
    evaluator : E => Try[Trilean])
      : Try[Trilean] =
  {
    reference match {
      case ShlurdEntityReference(word, determiner, count) => {
        val lemma = word.lemma
        world.resolveEntity(
          lemma, context,
          world.qualifierSet(
            ShlurdReference.extractQualifiers(specifiedState))) match
        {
          case Success(unfilteredEntities) => {
            debug(s"CANDIDATE ENTITIES : $unfilteredEntities")
            // probably we should be pushing filters down into resolveEntity
            // for efficiency
            val locationStates =
              ShlurdReference.extractLocationSpecifiers(specifiedState)
            val entities = {
              if (locationStates.isEmpty) {
                unfilteredEntities
              } else {
                // should probably be doing some caching for
                // reference -> entity lookups
                unfilteredEntities.filter(subjectEntity =>
                  locationStates.forall(ls => {
                    val locative = ls.locative
                    val evaluation = evaluatePredicateOverReference(
                      ls.location, REF_LOCATION, new ResultCollector[E])
                    {
                      locationEntity => {
                        val qualifiers : Set[String] = {
                          if (locative == LOC_GENITIVE_OF) {
                            Set(lemma)
                          } else {
                            Set.empty
                          }
                        }
                        val result = world.evaluateEntityLocationPredicate(
                          subjectEntity, locationEntity, locative, qualifiers)
                        debug("RESULT FOR " +
                          s"$subjectEntity $locative $locationEntity " +
                          s"with $qualifiers is $result")
                        result
                      }
                    }
                    if (evaluation.isFailure) {
                      return evaluation
                    } else {
                      evaluation.get.isTrue
                    }
                  }
                ))
              }
            }
            determiner match {
              case DETERMINER_UNIQUE | DETERMINER_UNSPECIFIED => {
                if (entities.isEmpty) {
                  fail(sentencePrinter.sb.respondNonexistent(lemma))
                } else {
                  count match {
                    case COUNT_SINGULAR => {
                      if (entities.size > 1) {
                        if (determiner == DETERMINER_UNIQUE) {
                          fail(sentencePrinter.sb.respondAmbiguous(lemma))
                        } else {
                          evaluateDeterminer(
                            entities.map(
                              invokeEvaluator(_, resultCollector, evaluator)),
                            DETERMINER_ANY)
                        }
                      } else {
                        invokeEvaluator(
                          entities.head, resultCollector, evaluator)
                      }
                    }
                    case COUNT_PLURAL => {
                      val newDeterminer = determiner match {
                        case DETERMINER_UNIQUE => DETERMINER_ALL
                        case _ => determiner
                      }
                      evaluateDeterminer(
                        entities.map(
                          invokeEvaluator(_, resultCollector, evaluator)),
                        newDeterminer)
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
          case Failure(e) => {
            debug("ERROR", e)
            fail(sentencePrinter.sb.respondUnknown(lemma))
          }
        }
      }
      case ShlurdPronounReference(person, gender, count) => {
        // FIXME for third-person, need conversational coreference resolution
        world.resolvePronoun(person, gender, count) match {
          case Success(entities) => {
            debug(s"CANDIDATE ENTITIES : $entities")
            evaluateDeterminer(
              entities.map(
                invokeEvaluator(_, resultCollector, evaluator)),
              DETERMINER_ALL)
          }
          case Failure(e) => {
            debug("ERROR", e)
            fail(sentencePrinter.sb.respondUnknownPronoun(
              sentencePrinter.print(
                reference, INFLECT_NOMINATIVE, ShlurdConjoining.NONE)))
          }
        }
      }
      case ShlurdConjunctiveReference(determiner, references, separator) => {
        val results = references.map(
          evaluatePredicateOverReference(
            _, context, resultCollector, specifiedState)(evaluator))
        evaluateDeterminer(results, determiner)
      }
      case ShlurdStateSpecifiedReference(sub, subState) => {
        evaluatePredicateOverState(
          sub, subState, context, resultCollector, specifiedState, evaluator)
      }
      case ShlurdGenitiveReference(genitive, sub) => {
        val state = ShlurdLocationState(LOC_GENITIVE_OF, genitive)
        evaluatePredicateOverState(
          sub, state, context, resultCollector, specifiedState, evaluator)
      }
      case _ : ShlurdUnknownReference => {
        debug("UNKNOWN REFERENCE")
        fail(sentencePrinter.sb.respondCannotUnderstand())
      }
    }
  }

  private def evaluatePredicateOverState(
    reference : ShlurdReference,
    state : ShlurdState,
    context : ShlurdReferenceContext,
    resultCollector : ResultCollector[E],
    specifiedState : ShlurdState,
    evaluator : E => Try[Trilean])
      : Try[Trilean] =
  {
    val combinedState = {
      if (specifiedState == ShlurdNullState()) {
        state
      } else {
        ShlurdConjunctiveState(
          DETERMINER_ALL,
          Seq(specifiedState, state),
          SEPARATOR_CONJOINED)
      }
    }
    evaluatePredicateOverReference(
      reference, context, resultCollector, combinedState)(evaluator)
  }

  private def invokeEvaluator(
    entity : E,
    resultCollector : ResultCollector[E],
    evaluator : E => Try[Trilean]) : Try[Trilean] =
  {
    val result = evaluator(entity)
    result.foreach(resultCollector.entityMap.put(entity, _))
    result
  }

  private def evaluateExistencePredicate(
    subjectRef : ShlurdReference, resultCollector : ResultCollector[E])
      : Try[Trilean] =
  {
    evaluatePredicateOverReference(subjectRef, REF_SUBJECT, resultCollector) {
      entity => Success(Trilean.True)
    }
  }

  private def evaluatePropertyStatePredicate(
    subjectRef : ShlurdReference,
    state : ShlurdWord,
    resultCollector : ResultCollector[E])
      : Try[Trilean] =
  {
    evaluatePredicateOverReference(subjectRef, REF_SUBJECT, resultCollector)
    {
      entity => {
        val result = world.resolveProperty(entity, state.lemma) match {
          case Success((property, stateName)) => {
            resultCollector.states += ShlurdWord(
              property.getStates()(stateName), stateName)
            world.evaluateEntityPropertyPredicate(
              entity, property, stateName)
          }
          case Failure(e) => Failure(e)
        }
        debug(s"RESULT FOR $entity is $result")
        result
      }
    }
  }

  private def evaluateLocationStatePredicate(
    subjectRef : ShlurdReference, locative : ShlurdLocative,
    locationRef : ShlurdReference,
    resultCollector : ResultCollector[E])
      : Try[Trilean] =
  {
    val locationCollector = new ResultCollector[E]
    evaluatePredicateOverReference(
      subjectRef, REF_LOCATED, resultCollector)
    {
      subjectEntity => {
        evaluatePredicateOverReference(
          locationRef, REF_LOCATION, locationCollector)
        {
          locationEntity => {
            val result = world.evaluateEntityLocationPredicate(
              subjectEntity, locationEntity, locative)
            debug("RESULT FOR " +
              s"$subjectEntity $locative $locationEntity is $result")
            result
          }
        }
      }
    }
  }

  private def rewriteQuery(
    predicate : ShlurdPredicate) : ShlurdPredicate =
  {
    val rewriteSpecifier =
      Rewriter.rule[ShlurdPhrase] {
        case ShlurdEntityReference(
          entity, DETERMINER_UNSPECIFIED, count
        ) =>
          {
            ShlurdEntityReference(entity, DETERMINER_ANY, count)
          }
      }
    def rewriteSubject(subject : ShlurdReference) = Rewriter.rewrite(
      Rewriter.everywherebu("rewriteSubject", rewriteSpecifier)
    )(subject)
    val rewritePredicate =
      Rewriter.rule[ShlurdPredicate] {
        case ShlurdStatePredicate(subject, state) => {
          ShlurdStatePredicate(
            rewriteSubject(subject), state)
        }
        case ShlurdRelationshipPredicate(subject, complement, relationship) => {
          ShlurdRelationshipPredicate(
            rewriteSubject(subject), complement, relationship)
        }
      }
    Rewriter.rewrite(rewritePredicate)(predicate)
  }
}
