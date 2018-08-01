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

import com.lingeringsocket.shlurd.parser._

import scala.collection.JavaConverters._

import ShlurdEnglishLemmas._

class SpcCreed(cosmos : SpcCosmos)
{
  def allBeliefs() : Iterable[SilSentence] =
  {
    cosmos.getIdealSynonyms.filterNot(
      pair => SpcPrimordial.isPrimordialSynonym(pair) || isTrivialSynonym(pair)
    ).map(
      formAliasBelief
    ) ++ (
      cosmos.getRoles.flatMap(roleTaxonomyBeliefs(_))
    ) ++ (
      cosmos.getRoles.flatMap(idealAssociationBeliefs(_))
    ) ++ (
      cosmos.getForms.flatMap(formBeliefs(_))
    ) ++ (
      cosmos.getInverseAssocEdges.flatMap(
        entry => inverseAssocBelief(entry._1, entry._2))
    ) ++ (
      cosmos.getEntities.flatMap(entityBeliefs(_))
    ) ++ (
      cosmos.getTriggers
    )
  }

  def formBeliefs(form : SpcForm) : Iterable[SilSentence] =
  {
    cosmos.getPropertyMap(form).values.map(
      formPropertyBelief(form, _)
    ) ++
    cosmos.getInflectedStateNormalizations(form).map(
      formStateNormalizationBelief(form, _)
    ) ++ {
      cosmos.getIdealTaxonomyGraph.outgoingEdgesOf(form).asScala.toSeq.map(
        formTaxonomyBelief(_))
    } ++ {
      idealAssociationBeliefs(form)
    }
  }

  def idealAssociationBeliefs(ideal : SpcIdeal) : Iterable[SilSentence] =
  {
    cosmos.getFormAssocGraph.outgoingEdgesOf(ideal).asScala.toSeq.map(
      idealAssociationBelief(_))
  }

  def roleBeliefs(role : SpcRole) : Iterable[SilSentence] =
  {
    roleTaxonomyBeliefs(role) ++ idealAssociationBeliefs(role)
  }

  def roleTaxonomyBeliefs(role : SpcRole) : Iterable[SilSentence] =
  {
    cosmos.getIdealTaxonomyGraph.outgoingEdgesOf(role).asScala.toSeq.
      filterNot(isTrivialTaxonomy).map(
        roleTaxonomyBelief(_))
  }

  def entityBeliefs(entity : SpcEntity) : Iterable[SilSentence] =
  {
    Seq(entityFormBelief(entity)) ++ {
      cosmos.getEntityAssocGraph.outgoingEdgesOf(entity).asScala.toSeq.map(
        entityAssociationBelief(_))
    }
  }

  def idealTaxonomyBelief(
    edge : SpcTaxonomyEdge
  ) : SilSentence =
  {
    cosmos.getGraph.getHyponymIdeal(edge) match {
      case _ : SpcForm => formTaxonomyBelief(edge)
      case _ : SpcRole => roleTaxonomyBelief(edge)
    }
  }

  def formTaxonomyBelief(
    edge : SpcTaxonomyEdge
  ) : SilSentence =
  {
    SilPredicateSentence(
      SilRelationshipPredicate(
        idealNoun(cosmos.getGraph.getHyponymIdeal(edge)),
        SilStateSpecifiedReference(
          nounReference(LEMMA_KIND),
          SilAdpositionalState(
            SilAdposition.OF,
            idealNoun(cosmos.getGraph.getHypernymIdeal(edge)))),
        REL_IDENTITY))
  }

  def roleTaxonomyBelief(
    edge : SpcTaxonomyEdge
  ) : SilSentence =
  {
    SilPredicateSentence(
      SilRelationshipPredicate(
        idealNoun(cosmos.getGraph.getHyponymIdeal(edge)),
        idealNoun(cosmos.getGraph.getHypernymIdeal(edge)),
        REL_IDENTITY),
      SilTam.indicative.withModality(MODAL_MUST))
  }

  def formAliasBelief(
    entry : (String, String)
  ) : SilSentence =
  {
    SilPredicateSentence(
      SilRelationshipPredicate(
        nounReference(entry._1),
        nounReference(entry._2),
        REL_IDENTITY))
  }

  def formPropertyBelief(
    form : SpcForm,
    property : SpcProperty
  ) : SilSentence =
  {
    val noun = {
      if (property.isSynthetic) {
        idealNoun(form)
      } else {
        SilGenitiveReference(
          idealNoun(form),
          nounReference(
            property.name, COUNT_SINGULAR, DETERMINER_UNSPECIFIED))
      }
    }
    SilPredicateSentence(
      SilStatePredicate(
        noun,
        {
          val propertyStates = cosmos.getPropertyStateMap(property)
          if (propertyStates.size == 1) {
            propertyState(propertyStates.head)
          } else {
            SilConjunctiveState(
              DETERMINER_ANY,
              propertyStates.map(propertyState).toSeq,
              SEPARATOR_CONJOINED)
          }
        }
      ),
      SilTam.indicative.withModality(
        if (property.isClosed) MODAL_MUST else MODAL_MAY)
    )
  }

  def formStateNormalizationBelief(
    form : SpcForm,
    entry : (SilState, SilState)
  ) : SilSentence =
  {
    SilPredicateSentence(
      SilStatePredicate(
        SilStateSpecifiedReference(
          idealNoun(form),
          entry._1),
        entry._2
      )
    )
  }

  def idealAssociationBelief(
    edge : SpcFormAssocEdge
  ) : SilSentence =
  {
    val constraint = edge.constraint
    val isProperty = edge.isProperty
    val (count, determiner) = {
      if (constraint.upper == 1) {
        (COUNT_SINGULAR, SilIntegerDeterminer(constraint.upper))
      } else {
        (COUNT_PLURAL, DETERMINER_UNSPECIFIED)
      }
    }
    val possesseeNoun = nounReference(
      edge.getRoleName, count, determiner)
    val possessee = {
      if (isProperty) {
        SilStateSpecifiedReference(
          possesseeNoun,
          SilAdpositionalState(
            SilAdposition.AS,
            nounReference(LEMMA_PROPERTY))
        )
      } else {
        possesseeNoun
      }
    }
    SilPredicateSentence(
      SilRelationshipPredicate(
        idealNoun(cosmos.getGraph.getPossessorIdeal(edge)),
        possessee,
        REL_ASSOCIATION),
      SilTam.indicative.withModality(
        if (constraint.lower == 0) MODAL_MAY else MODAL_MUST)
    )
  }

  def entityFormBelief(
    entity : SpcEntity
  ) : SilSentence =
  {
    val subject = cosmos.specificReference(entity, DETERMINER_NONSPECIFIC)
    val predicate = entity.properName match {
      case "" => {
        SilStatePredicate(
          subject,
          SilExistenceState())
      }
      case _ => {
        SilRelationshipPredicate(
          subject,
          idealNoun(entity.form),
          REL_IDENTITY)
      }
    }
    SilPredicateSentence(predicate)
  }

  def entityAssociationBelief(
    edge : SpcEntityAssocEdge
  ) : SilSentence =
  {
    val possessor = cosmos.specificReference(
      cosmos.getGraph.getPossessorEntity(edge), DETERMINER_NONSPECIFIC)
    val possessee = cosmos.specificReference(
      cosmos.getGraph.getPossesseeEntity(edge), DETERMINER_NONSPECIFIC)
    val role = nounReference(
      edge.getRoleName, COUNT_SINGULAR, DETERMINER_UNSPECIFIED)
    SilPredicateSentence(
      SilRelationshipPredicate(
        possessee,
        SilGenitiveReference(
          possessor,
          role),
        REL_IDENTITY)
    )
  }

  private def isTrivialTaxonomy(
    edge : SpcTaxonomyEdge) =
  {
    cosmos.getGraph.getHypernymIdeal(edge).name.equals(
      cosmos.getGraph.getHyponymIdeal(edge).name)
  }

  private def isTrivialSynonym(
    pair : (String, String)) =
  {
    pair._1 == pair._2
  }

  def inverseAssocBelief(
    edge1 : SpcFormAssocEdge,
    edge2 : SpcFormAssocEdge
  ) : Option[SilSentence] =
  {
    val possessorIdeal = cosmos.getGraph.getPossessorIdeal(edge1) match {
      case form : SpcForm => form
      case role : SpcRole => {
        val forms = cosmos.getGraph.getFormsForRole(role)
        // FIXME
        assert(forms.size < 2)
        if (forms.isEmpty) {
          return None
        } else {
          forms.head
        }
      }
    }
    val sentence =
      SilPredicateSentence(
        SilRelationshipPredicate(
          SilStateSpecifiedReference(
            idealNoun(possessorIdeal),
            SilAdpositionalState(
              SilAdposition.WITH,
              nounReference(edge1.getRoleName))),
          nounReference(edge2.getRoleName),
          REL_IDENTITY))
    Some(sentence)
  }

  private def nounReference(
    noun : String, count : SilCount = COUNT_SINGULAR,
    determiner : SilDeterminer = DETERMINER_NONSPECIFIC) =
  {
    count match {
      case COUNT_SINGULAR => {
        SilNounReference(SilWord(noun), determiner)
      }
      case COUNT_PLURAL => {
        SilNounReference(
          SilWord("", noun), DETERMINER_UNSPECIFIED, COUNT_PLURAL)
      }
    }
  }

  private def idealNoun(
    ideal : SpcIdeal, count : SilCount = COUNT_SINGULAR) =
  {
    nounReference(ideal.name, count)
  }

  private def propertyState(entry : (String, String)) =
  {
    SilPropertyState(SilWord(entry._2, entry._1))
  }
}
