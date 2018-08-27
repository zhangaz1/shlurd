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
package com.lingeringsocket.shlurd.mind

import com.lingeringsocket.shlurd.parser._
import com.lingeringsocket.shlurd.ilang._

import scala.util._

import spire.math._

import scala.collection._

import SprEnglishLemmas._

class SmcPredicateEvaluator[
  EntityType<:SilEntity,
  PropertyType<:SmcProperty,
  CosmosType<:SmcCosmos[EntityType, PropertyType],
  MindType<:SmcMind[EntityType, PropertyType, CosmosType]
](
  mind : MindType,
  sentencePrinter : SilSentencePrinter,
  debugger : Option[SmcDebugger])
    extends SmcDebuggable(debugger)
{
  type ResultCollectorType = SmcResultCollector[EntityType]

  type EntityPredicateEvaluator = (EntityType, SilReference) => Try[Trilean]

  private def cosmos = mind.getCosmos

  private def fail(msg : String) = cosmos.fail(msg)

  protected[mind] def evaluatePredicate(
    predicate : SilPredicate,
    resultCollector : ResultCollectorType) : Try[Trilean] =
  {
    debug(s"EVALUATE PREDICATE : $predicate")
    debugPushLevel()
    try {
      resolveReferences(predicate, resultCollector)
    } catch {
      case ex : RuntimeException => {
        return Failure(ex)
      }
    }
    // FIXME analyze verb modifiers
    val result = predicate match {
      case SilStatePredicate(subject, state, modifiers) => {
        state match {
          case SilConjunctiveState(determiner, states, _) => {
            // FIXME:  how to write to resultCollector.entityMap in this case?
            val tries = states.map(
              s => evaluatePredicate(
                SilStatePredicate(subject, s), resultCollector))
            evaluateDeterminer(tries, determiner)
          }
          case _ => evaluateNormalizedStatePredicate(
            subject, state, resultCollector)
        }
      }
      case SilRelationshipPredicate(
        subjectRef, complementRef, relationship, modifiers) =>
      {
        val subjectCollector = chooseResultCollector(
          subjectRef, resultCollector)
        val complementCollector = chooseResultCollector(
          complementRef, resultCollector)
        val categoryLabel = relationship match {
          case REL_IDENTITY => extractCategory(complementRef)
          case _ => ""
        }
        evaluatePredicateOverReference(
          subjectRef, REF_SUBJECT, subjectCollector)
        {
          (subjectEntity, entityRef) => {
            if (!categoryLabel.isEmpty) {
              resultCollector.isCategorization = true
              evaluateCategorization(subjectEntity, categoryLabel)
            } else {
              val context = relationship match {
                case REL_IDENTITY => REF_COMPLEMENT
                case REL_ASSOCIATION => REF_SUBJECT
              }
              if (relationship == REL_ASSOCIATION) {
                val roleQualifiers = extractRoleQualifiers(complementRef)
                if (roleQualifiers.size == 1) {
                  val roleName = roleQualifiers.head
                  cosmos.reifyRole(subjectEntity, roleName, true)
                }
              }
              evaluatePredicateOverReference(
                complementRef, context, complementCollector)
              {
                (complementEntity, entityRef) => evaluateRelationshipPredicate(
                  subjectEntity, complementRef, complementEntity, relationship
                )
              }
            }
          }
        }
      }
      case ap : SilActionPredicate => {
        // FIXME we should be calling updateNarrative() here too for
        // indicative statements
        evaluateActionPredicate(ap, resultCollector)
      }
      case _ => {
        debug("UNEXPECTED PREDICATE TYPE")
        fail(sentencePrinter.sb.respondCannotUnderstand)
      }
    }
    debugPopLevel()
    debug(s"PREDICATE TRUTH : $result")
    result
  }

  protected def evaluateActionPredicate(
    ap : SilActionPredicate,
    resultCollector : ResultCollectorType) : Try[Trilean] =
  {
    debug("ACTION PREDICATES UNSUPPORTED")
    fail(sentencePrinter.sb.respondCannotUnderstand)
  }

  def resolveReferences(
    phrase : SilPhrase,
    resultCollector : ResultCollectorType)
  {
    val resolver = new SmcReferenceResolver(
      cosmos, sentencePrinter, resultCollector)
    resolver.resolve(phrase)
  }

  private def evaluatePropertyStateQuery(
    entity : EntityType,
    entityRef : SilReference,
    propertyName : String,
    resultCollector : ResultCollectorType)
      : Try[Trilean] =
  {
    val result = cosmos.evaluateEntityProperty(entity, propertyName) match {
      case Success((Some(actualProperty), Some(stateName))) => {
        resultCollector.states += SilWord(
          cosmos.getPropertyStateMap(actualProperty).get(stateName).
            getOrElse(stateName), stateName)
        Success(Trilean.True)
      }
      case Success((_, _)) => {
        Success(Trilean.Unknown)
      }
      case Failure(e) => {
        debug("PROPERTY EVALUATION ERROR", e)
        Failure(e)
      }
    }
    debug(s"RESULT FOR $entity is $result")
    result
  }

  private def evaluateNormalizedStatePredicate(
    subjectRef : SilReference,
    originalState : SilState,
    resultCollector : ResultCollectorType)
      : Try[Trilean] =
  {
    val context = originalState match {
      case _ : SilAdpositionalState => REF_ADPOSITION_SUBJ
      case _ => REF_SUBJECT
    }
    evaluatePredicateOverReference(subjectRef, context, resultCollector)
    {
      (entity, entityRef) => {
        val normalizedState = cosmos.normalizeState(entity, originalState)
        if (originalState != normalizedState) {
          debug(s"NORMALIZED STATE : $normalizedState")
        }
        normalizedState match {
          case SilExistenceState() => {
            Success(Trilean.True)
          }
          case SilPropertyState(word) => {
            evaluatePropertyStatePredicate(
              entity, entityRef, word, resultCollector)
          }
          case SilPropertyQueryState(propertyName) => {
            evaluatePropertyStateQuery(
              entity, entityRef, propertyName, resultCollector)
          }
          case SilAdpositionalState(adposition, objRef) => {
            evaluateAdpositionStatePredicate(
              entity, adposition, objRef, resultCollector)
          }
          case _ => {
            debug(s"UNEXPECTED STATE : $normalizedState")
            fail(sentencePrinter.sb.respondCannotUnderstand)
          }
        }
      }
    }
  }

  private def evaluateRelationshipPredicate(
    subjectEntity : EntityType,
    complementRef : SilReference,
    complementEntity : EntityType,
    relationship : SilRelationship) : Try[Trilean] =
  {
    relationship match {
      case REL_IDENTITY => {
        val result = {
          if (subjectEntity.isTentative || complementEntity.isTentative) {
            Success(Trilean.Unknown)
          } else {
            Success(Trilean(subjectEntity == complementEntity))
          }
        }
        debug("RESULT FOR " +
          s"$subjectEntity == $complementEntity is $result")
        result
      }
      case REL_ASSOCIATION => {
        val roleQualifiers = extractRoleQualifiers(complementRef)
        val result = cosmos.evaluateEntityAdpositionPredicate(
          complementEntity, subjectEntity,
          SilAdposition.GENITIVE_OF, roleQualifiers)
        debug("RESULT FOR " +
          s"$complementEntity GENITIVE_OF " +
          s"$subjectEntity with $roleQualifiers is $result")
        result
      }
    }
  }

  private def evaluatePredicateOverReference(
    reference : SilReference,
    context : SilReferenceContext,
    resultCollector : ResultCollectorType,
    specifiedState : SilState = SilNullState()
  )(evaluator : EntityPredicateEvaluator)
      : Try[Trilean] =
  {
    debug("EVALUATE PREDICATE OVER REFERENCE : " +
      reference + " WITH CONTEXT " + context + " AND SPECIFIED STATE "
      + specifiedState)
    debugPushLevel()
    val result = evaluatePredicateOverReferenceImpl(
      reference, context, resultCollector,
      specifiedState, evaluator)
    debugPopLevel()
    debug(s"PREDICATE TRUTH OVER REFERENCE : $result")
    result
  }

  private def evaluatePredicateOverEntities(
    unfilteredEntities : Iterable[EntityType],
    entityRef : SilReference,
    context : SilReferenceContext,
    resultCollector : ResultCollectorType,
    specifiedState : SilState,
    determiner : SilDeterminer,
    count : SilCount,
    noun : SilWord,
    evaluator : EntityPredicateEvaluator)
      : Try[Trilean] =
  {
    debug(s"CANDIDATE ENTITIES : $unfilteredEntities")
    // probably we should be pushing filters down into
    // resolveQualifiedNoun for efficiency
    val adpositionStates =
      SilReference.extractAdpositionSpecifiers(specifiedState)
    val entities = {
      if (adpositionStates.isEmpty) {
        unfilteredEntities
      } else {
        unfilteredEntities.filter(subjectEntity =>
          adpositionStates.forall(adp => {
            val adposition = adp.adposition
            val qualifiers : Set[String] = {
              if (adposition == SilAdposition.GENITIVE_OF) {
                Set(noun.lemma)
              } else {
                Set.empty
              }
            }
            val evaluation = evaluatePredicateOverReference(
              adp.objRef, REF_ADPOSITION_OBJ,
                resultCollector.spawn)
            {
              (objEntity, entityRef) => {
                val result = cosmos.evaluateEntityAdpositionPredicate(
                  subjectEntity, objEntity, adposition, qualifiers)
                debug("RESULT FOR " +
                  s"$subjectEntity $adposition $objEntity " +
                  s"with $qualifiers is $result")
                result
              }
            }
            if (evaluation.isFailure) {
              return evaluation
            } else {
              evaluation.get.isTrue
            }
          })
        )
      }
    }
    resultCollector.referenceMap.put(
      entityRef, SprUtils.orderedSet(entities))
    determiner match {
      case DETERMINER_UNIQUE | DETERMINER_UNSPECIFIED => {
        if (entities.isEmpty && (context != REF_COMPLEMENT)) {
          fail(sentencePrinter.sb.respondNonexistent(noun))
        } else {
          count match {
            case COUNT_SINGULAR => {
              if (entities.isEmpty) {
                Success(Trilean.False)
              } else if (entities.size > 1) {
                if (determiner == DETERMINER_UNIQUE) {
                  fail(sentencePrinter.sb.respondAmbiguous(
                    noun))
                } else {
                  evaluateDeterminer(
                    entities.map(
                      invokeEvaluator(
                        _, entityRef, resultCollector, evaluator)),
                    DETERMINER_ANY)
                }
              } else {
                invokeEvaluator(
                  entities.head, entityRef, resultCollector, evaluator)
              }
            }
            case COUNT_PLURAL => {
              val newDeterminer = determiner match {
                case DETERMINER_UNIQUE => DETERMINER_ALL
                case _ => determiner
              }
              evaluateDeterminer(
                entities.map(
                  invokeEvaluator(_, entityRef, resultCollector, evaluator)),
                newDeterminer)
            }
          }
        }
      }
      case _ => {
        evaluateDeterminer(
          entities.map(invokeEvaluator(
            _, entityRef, resultCollector, evaluator)),
          determiner)
      }
    }
  }

  private def evaluatePredicateOverReferenceImpl(
    reference : SilReference,
    context : SilReferenceContext,
    resultCollector : ResultCollectorType,
    specifiedState : SilState,
    evaluator : EntityPredicateEvaluator)
      : Try[Trilean] =
  {
    val referenceMap = resultCollector.referenceMap
    // FIXME should maybe use normalizeState here, but it's a bit tricky
    reference match {
      case SilNounReference(noun, determiner, count) => {
        // FIXME should verify that specifiedState hasn't changed
        // from when result was cached?
        val entitiesTry = referenceMap.get(reference) match {
          case Some(entities) => Success(entities)
          case _ => {
            cosmos.resolveQualifiedNoun(
              noun.lemma, context,
              cosmos.qualifierSet(
                SilReference.extractQualifiers(specifiedState)))
          }
        }
        entitiesTry match {
          case Success(entities) => {
            evaluatePredicateOverEntities(
              entities,
              reference,
              context,
              resultCollector,
              specifiedState,
              determiner,
              count,
              noun,
              evaluator)
          }
          case Failure(e) => {
            debug("ERROR", e)
            fail(sentencePrinter.sb.respondUnknown(noun))
          }
        }
      }
      case pr : SilPronounReference => {
        assert(specifiedState == SilNullState())
        val entitiesTry = referenceMap.get(reference) match {
          case Some(entities) => Success(entities)
          case _ => mind.resolvePronoun(pr).map(entities => {
            referenceMap.put(reference, entities)
            entities
          })
        }
        entitiesTry match {
          case Success(entities) => {
            debug(s"CANDIDATE ENTITIES : $entities")
            evaluateDeterminer(
              entities.map(
                invokeEvaluator(_, reference, resultCollector, evaluator)),
              DETERMINER_ALL)
          }
          case Failure(e) => {
            debug("ERROR", e)
            fail(sentencePrinter.sb.respondUnknownPronoun(
              sentencePrinter.print(
                reference, INFLECT_NOMINATIVE, SilConjoining.NONE)))
          }
        }
      }
      case SilConjunctiveReference(determiner, references, separator) => {
        val results = references.map(
          evaluatePredicateOverReference(
            _, context, resultCollector, specifiedState)(evaluator))
        val combinedEntities = references.flatMap(sub => {
          referenceMap.get(sub) match {
            case Some(entities) => entities
            case _ => Seq.empty
          }
        })
        referenceMap.put(reference, combinedEntities.toSet)
        evaluateDeterminer(results, determiner)
      }
      case SilStateSpecifiedReference(sub, subState) => {
        val result = evaluatePredicateOverState(
          sub, subState, context, resultCollector, specifiedState, evaluator)
        referenceMap.get(sub).foreach(
          entitySet => referenceMap.put(reference, entitySet))
        result
      }
      case SilGenitiveReference(possessor, possessee) => {
        val state = SilAdpositionalState(SilAdposition.GENITIVE_OF, possessor)
        val result = evaluatePredicateOverState(
          possessee, state, context, resultCollector, specifiedState, evaluator)
        referenceMap.get(possessee).foreach(
          entitySet => referenceMap.put(reference, entitySet))
        result
      }
      case rr : SilResolvedReference[EntityType] => {
        evaluatePredicateOverEntities(
          rr.entities,
          rr,
          context,
          resultCollector,
          specifiedState,
          rr.determiner,
          SilReference.getCount(rr),
          rr.noun,
          evaluator)
      }
      case _ : SilUnknownReference => {
        debug("UNKNOWN REFERENCE")
        fail(sentencePrinter.sb.respondCannotUnderstand)
      }
    }
  }

  private def evaluatePredicateOverState(
    reference : SilReference,
    state : SilState,
    context : SilReferenceContext,
    resultCollector : ResultCollectorType,
    specifiedState : SilState,
    evaluator : EntityPredicateEvaluator)
      : Try[Trilean] =
  {
    val combinedState = {
      if (specifiedState == SilNullState()) {
        state
      } else {
        SilConjunctiveState(
          DETERMINER_ALL,
          Seq(specifiedState, state),
          SEPARATOR_CONJOINED)
      }
    }
    evaluatePredicateOverReference(
      reference, context, resultCollector, combinedState)(evaluator)
  }

  private def evaluatePropertyStatePredicate(
    entity : EntityType,
    entityRef : SilReference,
    state : SilWord,
    resultCollector : ResultCollectorType)
      : Try[Trilean] =
  {
    val result = cosmos.resolvePropertyState(entity, state.lemma) match {
      case Success((property, stateName)) => {
        resultCollector.states += SilWord(
          cosmos.getPropertyStateMap(property).get(stateName).
            getOrElse(stateName), stateName)
        cosmos.evaluateEntityPropertyPredicate(
          entity, property, stateName)
      }
      case Failure(e) => {
        debug("ERROR", e)
        val errorRef = entityRef match {
          case SilNounReference(noun, determiner, count) => {
            val rephrased = noun match {
              case SilWord(LEMMA_WHO, LEMMA_WHO) => SilWord(LEMMA_PERSON)
              case SilWord(LEMMA_WHOM, LEMMA_WHOM) => SilWord(LEMMA_PERSON)
              case SilWord(LEMMA_WHERE, LEMMA_WHERE) => SilWord(LEMMA_CONTAINER)
              case _ => noun
            }
            val rephrasedDeterminer = determiner match {
              case DETERMINER_ANY | DETERMINER_SOME => DETERMINER_NONSPECIFIC
              case _ => determiner
            }
            SilNounReference(rephrased, rephrasedDeterminer, count)
          }
          case _ => {
            cosmos.specificReference(entity, DETERMINER_NONSPECIFIC)
          }
        }
        fail(sentencePrinter.sb.respondUnknownState(
          sentencePrinter.print(
            errorRef,
            INFLECT_NOMINATIVE,
            SilConjoining.NONE),
          state))
      }
    }
    debug(s"RESULT FOR $entity is $result")
    result
  }

  private def evaluateAdpositionStatePredicate(
    subjectEntity : EntityType, adposition : SilAdposition,
    objRef : SilReference,
    resultCollector : ResultCollectorType)
      : Try[Trilean] =
  {
    val objCollector = resultCollector.spawn
    evaluatePredicateOverReference(
      objRef, REF_ADPOSITION_OBJ, objCollector)
    {
      (objEntity, entityRef) => {
        val result = cosmos.evaluateEntityAdpositionPredicate(
          subjectEntity, objEntity, adposition)
        debug("RESULT FOR " +
          s"$subjectEntity $adposition $objEntity is $result")
        result
      }
    }
  }

  private def extractCategory(reference : SilReference) : String =
  {
    // FIXME:  support qualifiers etc
    reference match {
      case SilNounReference(
        noun, DETERMINER_NONSPECIFIC, COUNT_SINGULAR) => noun.lemma
      case _ => ""
    }
  }

  private def extractRoleQualifiers(complementRef : SilReference)
      : Set[String] =
  {
    // FIXME:  do something less hacky
    complementRef match {
      case SilNounReference(noun, determiner, count) => {
        Set(noun.lemma)
      }
      case _ => Set.empty
    }
  }

  private def evaluateCategorization(
    entity : EntityType,
    categoryLabel : String) : Try[Trilean] =
  {
    val result = cosmos.evaluateEntityCategoryPredicate(entity, categoryLabel)
    debug("RESULT FOR " +
      s"$entity IN_CATEGORY " +
      s"$categoryLabel is $result")
    result match {
      case Failure(e) => {
        debug("ERROR", e)
        fail(sentencePrinter.sb.respondUnknown(SilWord(categoryLabel)))
      }
      case _ => result
    }
  }

  private def invokeEvaluator(
    entity : EntityType,
    entityRef : SilReference,
    resultCollector : ResultCollectorType,
    evaluator : EntityPredicateEvaluator) : Try[Trilean] =
  {
    val result = evaluator(entity, entityRef)
    result.foreach(resultCollector.entityMap.put(entity, _))
    result
  }

  private def evaluateDeterminer(
    tries : Iterable[Try[Trilean]], determiner : SilDeterminer)
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
            if (results.isEmpty) {
              // FIXME:  logic dictates otherwise
              Success(Trilean.False)
            } else {
              Success(results.fold(Trilean.True)(_&_))
            }
          }
          case DETERMINER_ANY | DETERMINER_SOME | DETERMINER_NONSPECIFIC => {
            Success(results.fold(Trilean.False)(_|_))
          }
          case _ => fail(sentencePrinter.sb.respondCannotUnderstand)
        }
      }
    }
  }

  private def chooseResultCollector(
    phrase : SilPhrase,
    collector : ResultCollectorType) =
  {
    val phraseQuerier = new SmcPhraseRewriter
    if (phraseQuerier.containsWildcard(phrase)) {
      collector
    } else {
      collector.spawn
    }
  }
}
