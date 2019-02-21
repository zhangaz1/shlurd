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
package com.lingeringsocket.shlurd.platonic

import com.lingeringsocket.shlurd._
import com.lingeringsocket.shlurd.parser._
import com.lingeringsocket.shlurd.mind._
import com.lingeringsocket.shlurd.ilang._

import scala.collection._
import scala.collection.JavaConverters._
import scala.util._

import spire.math._

import SprEnglishLemmas._

class SpcMind(cosmos : SpcCosmos)
    extends SmcMind[SpcEntity, SpcProperty, SpcCosmos](cosmos)
{
  override def getCosmos = cosmos

  override def spawn(newCosmos : SpcCosmos) =
  {
    val mind = new SpcMind(newCosmos)
    mind.initFrom(this)
    mind
  }

  override def equivalentReferences(
    entity : SpcEntity,
    determiner : SilDeterminer) =
  {
    val assocGraph = cosmos.getEntityAssocGraph
    val genitives = assocGraph.incomingEdgesOf(entity).asScala.toSeq.flatMap(
      edge => {
        val graph = cosmos.getGraph
        val possessor = graph.getPossessorEntity(edge)
        val role = graph.getPossesseeRole(edge.formEdge)
        val specializedRole = graph.specializeRoleForForm(
          role,
          entity.form)
        // we prefer more specific associations over less specific ones
        // e.g. "Larry's father" is better than "one of Pete's uncles"
        val cardinality = assocGraph.outgoingEdgesOf(possessor).asScala.
          count(edge2 => {
            (edge2.getRoleName == edge.getRoleName) &&
            (role == specializedRole) || graph.isHyponym(
              specializedRole,
              graph.getPossesseeEntity(edge2).form)
          })
        val genitive = {
          if (cardinality > 1) {
            // "one of Pete's uncles"
            SilStateSpecifiedReference(
              SilNounReference(SilWord(LEMMA_ONE)),
              SilAdpositionalState(
                SilAdposition.OF,
                SilGenitiveReference(
                  cosmos.specificReference(possessor, determiner),
                  SilNounReference(SilWord.uninflected(specializedRole.name),
                    DETERMINER_UNSPECIFIED,
                    COUNT_PLURAL))))
          } else {
            // "Larry's father"
            SilGenitiveReference(
              cosmos.specificReference(possessor, determiner),
              SilNounReference(SilWord(specializedRole.name)))
          }
        }
        if (SpcMeta.isMetaEntity(possessor)) {
          None
        } else {
          Some((genitive, cardinality))
        }
      }
    )
    val qualifiedSeq = {
      if (!entity.properName.isEmpty) {
        Seq(cosmos.qualifiedReference(entity, DETERMINER_NONSPECIFIC))
      } else {
        Seq.empty
      }
    }
    super.equivalentReferences(entity, determiner) ++
      genitives.sortBy(_._2).map(_._1) ++ qualifiedSeq
  }

  override def thirdPersonReference(entities : Set[SpcEntity])
      : Option[SilReference] =
  {
    val gender = {
      if (entities.size == 1) {
        val entity = entities.head
        cosmos.evaluateEntityProperty(entity, LEMMA_GENDER) match {
          case Success((_, Some(LEMMA_FEMININE))) => GENDER_F
          case Success((_, Some(LEMMA_MASCULINE))) => GENDER_M
          case _ => GENDER_N
        }
      } else {
        // FIXME:  for languages like Spanish, need to be macho
        GENDER_N
      }
    }
    val count = {
      if (entities.size == 1) {
        COUNT_SINGULAR
      } else {
        COUNT_PLURAL
      }
    }
    Some(SilPronounReference(PERSON_THIRD, gender, count))
  }

  def resolveFormCandidates(noun : SilWord) : Seq[SpcForm] =
  {
    cosmos.resolveForm(noun.lemma).toSeq
  }

  def resolveForm(noun : SilWord) : Option[SpcForm] =
  {
    resolveFormCandidates(noun).headOption
  }

  def resolveRole(form : SpcForm, noun : SilWord) : Option[SpcRole] =
  {
    cosmos.resolveRole(noun.lemma)
  }

  def instantiateForm(noun : SilWord) : SpcForm =
  {
    resolveForm(noun).getOrElse {
      cosmos.instantiateForm(noun)
    }
  }

  def instantiateIdeal(
    noun : SilWord,
    assumeRole : Boolean = false) : SpcIdeal =
  {
    resolveForm(noun).getOrElse {
      cosmos.instantiateIdeal(noun, assumeRole)
    }
  }

  override def reifyRole(
    possessor : SpcEntity,
    roleName : SilWord,
    onlyIfProven : Boolean)
  {
    resolveRole(possessor.form, roleName) match {
      case Some(role) => {
        cosmos.reifyRole(possessor, role, onlyIfProven)
      }
      // FIXME assert instead?
      case _ =>
    }
  }

  override def evaluateEntityAdpositionPredicate(
    entity : SpcEntity,
    objRef : SpcEntity,
    adposition : SilAdposition,
    qualifiers : Set[SilWord]) : Try[Trilean] =
  {
    val roleName = adposition match {
      case SilAdposition.GENITIVE_OF => {
        if (qualifiers.size != 1) {
          return Success(Trilean.Unknown)
        } else {
          qualifiers.head
        }
      }
      case SilAdposition.IN => {
        SilWord(SmcLemmas.LEMMA_CONTAINEE)
      }
      case _ => {
        return Success(Trilean.Unknown)
      }
    }
    resolveRole(objRef.form, roleName) match {
      case Some(role) => {
        Success(Trilean(cosmos.isEntityAssoc(objRef, entity, role)))
      }
      case _ => {
        Success(Trilean.Unknown)
      }
    }
  }

  override def evaluateEntityCategoryPredicate(
    entity : SpcEntity,
    noun : SilWord,
    qualifiers : Set[String]) : Try[Trilean] =
  {
    val (formSeq, roleOpt) = cosmos.resolveIdeal(noun.lemma) match {
      case (None, None) => {
        resolveFormCandidates(noun) match {
          case Seq() => tupleN((Seq.empty, None))
          case seq => tupleN((seq, None))
        }
      }
      case (Some(form), roleOpt) => {
        tupleN((Seq(form), roleOpt))
      }
      case (None, roleOpt) => {
        tupleN((Seq.empty, roleOpt))
      }
    }
    val graph = cosmos.getGraph
    roleOpt match {
      case Some(role) => {
        Success(Trilean(
          graph.isFormCompatibleWithRole(entity.form, role) &&
            cosmos.getEntityAssocGraph.incomingEdgesOf(entity).asScala.
            exists(edge =>
              graph.isHyponym(
                role,
                graph.getPossesseeRole(edge.formEdge)))))
      }
      case _ => {
        formSeq match {
          case Seq() => {
            cosmos.fail(s"unknown ideal ${noun.lemma}")
          }
          case _ => {
            if (formSeq.exists(form => graph.isHyponym(entity.form, form))) {
              Success(Trilean.True)
            } else {
              if (entity.form.isTentative) {
                Success(Trilean.Unknown)
              } else {
                Success(Trilean.False)
              }
            }
          }
        }
      }
    }
  }
}
