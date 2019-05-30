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

import com.lingeringsocket.shlurd._
import com.lingeringsocket.shlurd.ilang._

import org.specs2.mutable._

import SprEnglishLemmas._

class SprParserSpec extends Specification
{
  private val NOUN_DOOR = SilWord("door")

  private val NOUN_PORTAL = SilWord("portal")

  private val NOUN_WINDOW = SilWord("window")

  private val NOUN_BATHROOM = SilWord("bathroom")

  private val NOUN_DOORS = SilWord("doors", "door")

  private val NOUN_PIGS = SilWord("pigs", "pig")

  private val NOUN_WHO = SilWord(LEMMA_WHO)

  private val NOUN_FRANNY = SilWord("Franny")

  private val NOUN_ZOOEY = SilWord("Zooey")

  private val NOUN_MOUSE = SilWord("mouse")

  private val NOUN_HOME = SilWord("home")

  private val NOUN_GRANDDAUGHTER = SilWord("granddaughter")

  private val NOUN_SISTER = SilWord("sister")

  private val NOUN_STEAK_KNIFE = SilCompoundWord(
    Seq(SilWord("steak"), SilWord("knife")))

  private val NOUN_BIG_TOP = SilCompoundWord(
    Seq(SilWord("big"), SilWord("top")))

  private val NOUN_LEMON_MERINGUE_PIE = SilCompoundWord(
    Seq(SilWord("lemon"), SilWord("meringue"), SilWord("pie")))

  private val STATE_OPEN = SilWord("open")

  private val ACTION_OPENS = SilWord("opens", "open")

  private val ACTION_OPEN = SilWord("open")

  private val ACTION_SAYS = SilWord("says", "say")

  private val ACTION_GIVE = SilWord("give")

  private val ACTION_KILL = SilWord("kill")

  private val STATE_CLOSE = SilWord("close")

  private val ACTION_CARRYING = SilWord("carrying", "carry")

  private val STATE_SHUT = SilWord("shut")

  private val STATE_CLOSED = SilWord("closed", "close")

  private val STATE_SIDEWAYS = SilWord("sideways")

  private val STATE_HUNGRY = SilWord("hungry")

  private val STATE_ON = SilWord("on")

  private val STATE_OFF = SilWord("off")

  private val QUALIFIER_BIG = SilWord("big")

  private val VERB_BE = SilWord("be", "be")

  private val VERB_AM = SilWord("am", "be")

  private val VERB_WAS = SilWord("was", "be")

  private val VERB_IS = SilWord("is", "be")

  private val VERB_ARE = SilWord("are", "be")

  private val VERB_TURN = SilWord("turn")

  private val VERB_BUMPS_OFF = SilCompoundWord(
    Seq(SilWord("bumps", "bump"), SilWord("off")))

  private def predTransitiveAction(
    subject : SilWord,
    verb : SilWord = ACTION_OPENS,
    directObject : SilWord = NOUN_DOOR,
    determiner : SilDeterminer = DETERMINER_UNIQUE,
    count : SilCount = COUNT_SINGULAR) =
  {
    SilActionPredicate(
      SilNounReference(subject),
      verb,
      Some(SilNounReference(directObject, determiner, count)))
  }

  private def predIntransitiveAction(
    subject : SilWord,
    verb : SilWord = ACTION_OPENS,
    determiner : SilDeterminer = DETERMINER_UNIQUE,
    count : SilCount = COUNT_SINGULAR) =
  {
    SilActionPredicate(
      SilNounReference(subject, determiner, count),
      verb)
  }

  private def predState(
    subject : SilWord,
    verb : SilWord = VERB_IS,
    state : SilWord = STATE_OPEN,
    determiner : SilDeterminer = DETERMINER_UNIQUE,
    count : SilCount = COUNT_SINGULAR) =
  {
    SilStatePredicate(
      SilNounReference(subject, determiner, count),
      verb,
      SilPropertyState(state))
  }

  private def predStateDoor(
    verb : SilWord = VERB_IS,
    state : SilWord = STATE_OPEN,
    determiner : SilDeterminer = DETERMINER_UNIQUE,
    count : SilCount = COUNT_SINGULAR) =
  {
    predState(NOUN_DOOR, verb, state, determiner, count)
  }

  private def stateCommandAction(
    pred : SilStatePredicate,
    changeVerb : Option[SilWord] = None,
    compound : Boolean = false,
    formality : SilFormality = SilFormality.DEFAULT) =
  {
    val stateWord = pred.state.asInstanceOf[SilPropertyState].state
    val (verbWord, modifiers) = changeVerb match {
      case Some(word : SilSimpleWord) => {
        if (compound) {
          tupleN((SilCompoundWord(
            Seq(word, stateWord.asInstanceOf[SilSimpleWord])),
            pred.modifiers))
        } else {
          tupleN((word,
            pred.modifiers :+ SilBasicVerbModifier(stateWord, 0)))
        }
      }
      case _ => {
        tupleN((stateWord, pred.modifiers))
      }
    }
    SilPredicateSentence(
      SilActionPredicate(
        SilPronounReference(PERSON_SECOND, GENDER_N, COUNT_SINGULAR),
        verbWord,
        Some(pred.subject),
        modifiers
      ),
      SilTam.imperative,
      formality
    )
  }

  private def parse(input : String) =
  {
    SprParser(input).parseOne
  }

  private def leaf(s : String) = SprSyntaxLeaf(s, s, s)

  private def leafCapitalized(s : String) = SprSyntaxLeaf(
    SprUtils.capitalize(s), s, SprUtils.capitalize(s))

  "SprParser" should
  {
    "parse a state predicate statement" in
    {
      val input = "the door is open"
      parse(input) must be equalTo
        SilPredicateSentence(predStateDoor())
      parse(input + ".") must be equalTo
        SilPredicateSentence(predStateDoor())
      parse(input + "!") must be equalTo
        SilPredicateSentence(predStateDoor(),
          SilTam.indicative, SilFormality(FORCE_EXCLAMATION))
      parse(input + "?") must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.interrogative)
    }

    "parse a transitive action predicate statement" in
    {
      val input = "Franny opens the door"
      parse(input) must be equalTo
        SilPredicateSentence(predTransitiveAction(NOUN_FRANNY))
      parse(input + "?") must be equalTo
        SilPredicateSentence(predTransitiveAction(NOUN_FRANNY),
          SilTam.interrogative)
    }

    "parse an intransitive action predicate statement" in
    {
      val input = "The door opens"
      parse(input) must be equalTo
        SilPredicateSentence(predIntransitiveAction(NOUN_DOOR))
      parse(input + "?") must be equalTo
        SilPredicateSentence(predIntransitiveAction(NOUN_DOOR),
          SilTam.interrogative)
    }

    "parse a quotation" in
    {
      if (SprParser.isCoreNLP) {
        skipped("CoreNLP not working")
      }
      val input = "Franny says \"I love you\""
      parse(input) must be equalTo
        SilPredicateSentence(
          SilActionPredicate(
            SilNounReference(NOUN_FRANNY),
            ACTION_SAYS,
            Some(SilQuotationReference("I love you"))))
    }

    "parse a negation" in
    {
      val input = "the door is not open"
      parse(input) must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.indicative.negative)
      val contracted = "the door isn't open"
      parse(input) must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.indicative.negative)
    }

    "parse compound words" in
    {
      if (SprParser.isCoreNLP) {
        skipped("CoreNLP not supported")
      }

      parse("there is a steak knife") must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilNounReference(NOUN_STEAK_KNIFE, DETERMINER_NONSPECIFIC),
            VERB_IS,
            SilExistenceState()))
      parse("there is a big top") must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilNounReference(NOUN_BIG_TOP, DETERMINER_NONSPECIFIC),
            VERB_IS,
            SilExistenceState()))
      parse("there is a lemon meringue pie") must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilNounReference(NOUN_LEMON_MERINGUE_PIE, DETERMINER_NONSPECIFIC),
            VERB_IS,
            SilExistenceState()))
      parse("Franny bumps off Zooey") must be equalTo
        SilPredicateSentence(
          predTransitiveAction(
            NOUN_FRANNY,
            VERB_BUMPS_OFF,
            NOUN_ZOOEY,
            DETERMINER_UNSPECIFIED))
    }

    "parse a state predicate question" in
    {
      val input = "is the door open"
      parse(input) must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.interrogative)
      parse(input + "?") must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.interrogative)
    }

    "parse a nominative enumeration question" in
    {
      val input = "which door is open"
      val expected = SilPredicateQuery(
        predStateDoor(
          VERB_IS, STATE_OPEN, DETERMINER_UNSPECIFIED, COUNT_SINGULAR),
        QUESTION_WHICH, INFLECT_NOMINATIVE, SilTam.interrogative)
      parse(input) must be equalTo expected
      parse(input + "?") must be equalTo expected
    }

    "parse an accusative enumeration question" in
    {
      val input = "which door must Franny open"
      val expected = SilPredicateQuery(
        predTransitiveAction(
          NOUN_FRANNY, ACTION_OPEN, NOUN_DOOR, DETERMINER_UNSPECIFIED),
        QUESTION_WHICH, INFLECT_ACCUSATIVE,
        SilTam.interrogative.withModality(MODAL_MUST))
      parse(input) must be equalTo expected
      parse(input + "?") must be equalTo expected
    }

    "parse a who identity question" in
    {
      val input = "who is at home"
      val expected = SilPredicateQuery(
        SilStatePredicate(
          SilNounReference(NOUN_WHO),
          VERB_IS,
          SilAdpositionalState(
            SilAdposition.AT,
            SilNounReference(NOUN_HOME))),
        QUESTION_WHO, INFLECT_NOMINATIVE, SilTam.interrogative)
      parse(input) must be equalTo expected
      parse(input + "?") must be equalTo expected
    }

    "parse a who nominative question" in
    {
      val input = "who opens the door"
      val expected = SilPredicateQuery(
        predTransitiveAction(NOUN_WHO),
        QUESTION_WHO, INFLECT_NOMINATIVE, SilTam.interrogative)
      parse(input) must be equalTo expected
      parse(input + "?") must be equalTo expected
    }

    "parse a who dative question" in
    {
      val input = "whom does Franny give the mouse to"
      val expected = SilPredicateQuery(
        predTransitiveAction(NOUN_FRANNY, ACTION_GIVE, NOUN_MOUSE).
          withNewModifiers(Seq(SilAdpositionalVerbModifier(
            SilAdposition.TO,
            SilNounReference(SilWord(LEMMA_WHOM))
          ))),
        QUESTION_WHO, INFLECT_ADPOSITIONED, SilTam.interrogative)
      parse(input) must be equalTo expected
      parse(input + "?") must be equalTo expected
    }

    "parse a quantity question" in
    {
      val input = "how many doors are open"
      val expected = SilPredicateQuery(
        predState(
          NOUN_DOORS, VERB_ARE, STATE_OPEN,
          DETERMINER_UNSPECIFIED, COUNT_PLURAL),
        QUESTION_HOW_MANY, INFLECT_NOMINATIVE, SilTam.interrogative)
      parse(input) must be equalTo expected
      parse(input + "?") must be equalTo expected
    }

    "parse an accusative progressive quantity question" in
    {
      val input = "how many pigs is Franny carrying"
      val expected = SilPredicateQuery(
        predTransitiveAction(
          NOUN_FRANNY, ACTION_CARRYING, NOUN_PIGS,
          DETERMINER_UNSPECIFIED, COUNT_PLURAL),
        QUESTION_HOW_MANY, INFLECT_ACCUSATIVE,
        SilTam.interrogative.progressive)
      parse(input) must be equalTo expected
      parse(input + "?") must be equalTo expected
    }

    "parse a negated question" in
    {
      val input = "is not the door open"
      parse(input) must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.interrogative.negative)
      parse(input + "?") must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.interrogative.negative)
    }

    "parse a negated question with contraction" in
    {
      val input = "isn't the door open"
      parse(input) must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.interrogative.negative)
      parse(input + "?") must be equalTo
        SilPredicateSentence(predStateDoor(), SilTam.interrogative.negative)
    }

    "parse a state change command" in
    {
      val input = "open the door"
      parse(input) must be equalTo
        stateCommandAction(predStateDoor())
      parse(input + ".") must be equalTo
        stateCommandAction(predStateDoor())
      if (false) {
        // this should probably work, but with a FORCE_PROPOSITIVE or
        // something like that?
        parse(input + "?") must be equalTo
          stateCommandAction(predStateDoor())
      }
      parse(input + "!") must be equalTo
        stateCommandAction(predStateDoor(),
          None,
          false,
          SilFormality(FORCE_EXCLAMATION))
    }

    "parse an action command" in
    {
      val input = "kill the pigs"
      parse(input) must be equalTo
        SilPredicateSentence(
          SilActionPredicate(
            SilPronounReference(PERSON_SECOND, GENDER_N, COUNT_SINGULAR),
            ACTION_KILL,
            Some(SilNounReference(NOUN_PIGS, DETERMINER_UNIQUE, COUNT_PLURAL))),
          SilTam.imperative)
    }

    "parse an identity statement" in
    {
      val input = "a portal is a door"
      parse(input) must be equalTo
        SilPredicateSentence(
          SilRelationshipPredicate(
            SilNounReference(
              NOUN_PORTAL, DETERMINER_NONSPECIFIC, COUNT_SINGULAR),
            VERB_IS,
            SilNounReference(
              NOUN_DOOR, DETERMINER_NONSPECIFIC, COUNT_SINGULAR)
          )
        )
    }

    "lemmatize correctly" in
    {
      val command = "close the door"
      parse(command) must be equalTo
        stateCommandAction(predStateDoor(VERB_BE, STATE_CLOSE))
      val question = "is the door closed"
      parse(question) must be equalTo
        SilPredicateSentence(
          predStateDoor(VERB_IS, STATE_CLOSED), SilTam.interrogative)
    }

    "parse adpositional verbs" in
    {
      if (SprParser.isCoreNLP) {
        skipped("CoreNLP not working")
      }
      parse("turn the door on") must be equalTo
        stateCommandAction(predStateDoor(VERB_BE, STATE_ON), Some(VERB_TURN))
      parse("turn on the door") must be equalTo
        stateCommandAction(
          predStateDoor(VERB_BE, STATE_ON), Some(VERB_TURN), true)
      parse("turn the door off") must be equalTo
        stateCommandAction(
          predStateDoor(VERB_BE, STATE_OFF), Some(VERB_TURN))
      parse("turn off the door") must be equalTo
        stateCommandAction(
          predStateDoor(VERB_BE, STATE_OFF), Some(VERB_TURN), true)
    }

    "parse adverbial state" in
    {
      val question = "is the door sideways"
      parse(question) must be equalTo
      SilPredicateSentence(
        predStateDoor(VERB_IS, STATE_SIDEWAYS), SilTam.interrogative)
    }

    "parse conjunctive state" in
    {
      val conjunction = "is the door open and sideways"
      parse(conjunction) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilNounReference(
              NOUN_DOOR, DETERMINER_UNIQUE, COUNT_SINGULAR),
            VERB_IS,
            SilConjunctiveState(
              DETERMINER_ALL,
              Seq(
                SilPropertyState(STATE_OPEN),
                SilPropertyState(STATE_SIDEWAYS)))),
          SilTam.interrogative)
      val disjunction = "is the door either open or closed"
      parse(disjunction) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilNounReference(
              NOUN_DOOR, DETERMINER_UNIQUE, COUNT_SINGULAR),
            VERB_IS,
            SilConjunctiveState(
              DETERMINER_UNIQUE,
              Seq(
                SilPropertyState(STATE_OPEN),
                SilPropertyState(STATE_CLOSED)))),
          SilTam.interrogative)
    }

    "parse problematic cases" in
    {
      skipped("maybe later")
      val inputEither = "open either door"
      parse(inputEither) must be equalTo
        stateCommandAction(predStateDoor(
          VERB_BE, STATE_OPEN, DETERMINER_UNIQUE))
    }

    "parse determiners" in
    {
      val inputThe = "open the door"
      parse(inputThe) must be equalTo
        stateCommandAction(predStateDoor(
          VERB_BE, STATE_OPEN, DETERMINER_UNIQUE))
      val inputAny = "open any door"
      parse(inputAny) must be equalTo
        stateCommandAction(predStateDoor(VERB_BE, STATE_OPEN, DETERMINER_ANY))
      val inputA = "open a door"
      parse(inputA) must be equalTo
        stateCommandAction(predStateDoor(
          VERB_BE, STATE_OPEN, DETERMINER_NONSPECIFIC))
      val inputSome = "open some door"
      parse(inputSome) must be equalTo
        stateCommandAction(predStateDoor(VERB_BE, STATE_OPEN, DETERMINER_SOME))
      val inputAll = "open all doors"
      parse(inputAll) must be equalTo
        stateCommandAction(
          predState(NOUN_DOORS, VERB_BE, STATE_OPEN,
            DETERMINER_ALL, COUNT_PLURAL))
      val inputNone = "open no door"
      parse(inputNone) must be equalTo
        stateCommandAction(predStateDoor(VERB_BE, STATE_OPEN, DETERMINER_NONE))

      val inputAnyQ = "is any door open"
      parse(inputAnyQ) must be equalTo
        SilPredicateSentence(
          predStateDoor(VERB_IS, STATE_OPEN, DETERMINER_ANY),
          SilTam.interrogative)
      val inputAllQ = "are all doors open"
      parse(inputAllQ) must be equalTo
        SilPredicateSentence(
          predState(NOUN_DOORS, VERB_ARE, STATE_OPEN,
            DETERMINER_ALL, COUNT_PLURAL),
          SilTam.interrogative)
    }

    "parse qualifiers" in
    {
      val inputFront = "open the big door"
      parse(inputFront) must be equalTo
        stateCommandAction(
          SilStatePredicate(
            SilReference.qualified(
              SilNounReference(NOUN_DOOR, DETERMINER_UNIQUE),
              Seq(QUALIFIER_BIG)),
            VERB_BE,
            SilPropertyState(STATE_OPEN)))
    }

    "parse adpositions" in
    {
      val input = "is Franny at home"
      parse(input) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilNounReference(NOUN_FRANNY),
            VERB_IS,
            SilAdpositionalState(
              SilAdposition.AT,
              SilNounReference(NOUN_HOME))),
          SilTam.interrogative)
    }

    "parse adposition specifiers" in
    {
      def pred(verb : SilWord = VERB_IS) = SilStatePredicate(
        SilStateSpecifiedReference(
          SilNounReference(
            NOUN_WINDOW, DETERMINER_UNIQUE, COUNT_SINGULAR),
          SilAdpositionalState(
            SilAdposition.IN,
            SilNounReference(
              NOUN_BATHROOM, DETERMINER_UNIQUE, COUNT_SINGULAR))),
        verb,
        SilPropertyState(STATE_OPEN)
      )
      parse("the window in the bathroom is open") must be equalTo
        SilPredicateSentence(
          pred(),
          SilTam.indicative)
      parse("is the window in the bathroom open") must be equalTo
        SilPredicateSentence(
          pred(),
          SilTam.interrogative)
      parse("open the window in the bathroom") must be equalTo
        stateCommandAction(
          pred(VERB_BE))
    }

    "parse verb modifiers" in
    {
      parse("where was the mouse before the bathroom") must be equalTo
        SilPredicateQuery(
          SilRelationshipPredicate(
            SilNounReference(SilWord(LEMMA_WHERE)),
            VERB_WAS,
            SilNounReference(NOUN_MOUSE, DETERMINER_UNIQUE),
            Seq(SilAdpositionalVerbModifier(
              SilAdposition(SilWord("before")),
              SilNounReference(NOUN_BATHROOM, DETERMINER_UNIQUE)
            ))
          ),
          QUESTION_WHERE,
          INFLECT_NOMINATIVE,
          SilTam.interrogative.past)
    }

    "parse pronouns" in
    {
      val input = "I am hungry"
      parse(input) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilPronounReference(PERSON_FIRST, GENDER_N, COUNT_SINGULAR),
            VERB_AM,
            SilPropertyState(STATE_HUNGRY)))
    }

    "parse possessive pronouns" in
    {
      val input = "is his granddaughter at home"
      parse(input) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilGenitiveReference(
              SilPronounReference(PERSON_THIRD, GENDER_M, COUNT_SINGULAR),
              SilNounReference(NOUN_GRANDDAUGHTER)),
            VERB_IS,
            SilAdpositionalState(
              SilAdposition.AT,
              SilNounReference(NOUN_HOME))),
          SilTam.interrogative)
    }

    "parse possessive reference" in
    {
      val input = "is Franny's mouse hungry"
      parse(input) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilGenitiveReference(
              SilNounReference(NOUN_FRANNY),
              SilNounReference(NOUN_MOUSE)),
            VERB_IS,
            SilPropertyState(STATE_HUNGRY)),
          SilTam.interrogative)
    }

    "parse genitive reference" in
    {
      val input = "Franny is Zooey's sister"
      parse(input) must be equalTo
        SilPredicateSentence(
          SilRelationshipPredicate(
            SilNounReference(NOUN_FRANNY),
            VERB_IS,
            SilGenitiveReference(
              SilNounReference(NOUN_ZOOEY),
              SilNounReference(NOUN_SISTER))
          ),
          SilTam.indicative)
    }

    "parse conjunctive reference" in
    {
      val inputPositive = "Franny and Zooey are hungry"
      parse(inputPositive) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilConjunctiveReference(
              DETERMINER_ALL,
              Seq(
                SilNounReference(NOUN_FRANNY),
                SilNounReference(NOUN_ZOOEY))),
            VERB_ARE,
            SilPropertyState(STATE_HUNGRY)))
      val inputNegative = "neither Franny nor Zooey is hungry"
      parse(inputNegative) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilConjunctiveReference(
              DETERMINER_NONE,
              Seq(
                SilNounReference(NOUN_FRANNY),
                SilNounReference(NOUN_ZOOEY))),
            VERB_IS,
            SilPropertyState(STATE_HUNGRY)))
    }

    "parse disjunctive reference" in
    {
      // this would be more natural as part of a conditional,
      // but whatever
      val inputInclusive = "Franny or Zooey is hungry"
      parse(inputInclusive) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilConjunctiveReference(
              DETERMINER_ANY,
              Seq(
                SilNounReference(NOUN_FRANNY),
                SilNounReference(NOUN_ZOOEY))),
            VERB_IS,
            SilPropertyState(STATE_HUNGRY)))

      // FIXME:  in this context, should really be DETERMINER_ANY
      // instead of DETERMINER_UNIQUE
      val inputExclusive = "either Franny or Zooey is hungry"
      parse(inputExclusive) must be equalTo
        SilPredicateSentence(
          SilStatePredicate(
            SilConjunctiveReference(
              DETERMINER_UNIQUE,
              Seq(
                SilNounReference(NOUN_FRANNY),
                SilNounReference(NOUN_ZOOEY))),
            VERB_IS,
            SilPropertyState(STATE_HUNGRY)))
    }

    "parse modals" in
    {
      parse("The door must be open") must be equalTo(
        SilPredicateSentence(
          predStateDoor(VERB_BE),
          SilTam.indicative.withModality(MODAL_MUST)))
      parse("Must the door be open") must be equalTo(
        SilPredicateSentence(
          predStateDoor(VERB_BE),
          SilTam.interrogative.withModality(MODAL_MUST)))
      parse("The door may be open") must be equalTo(
        SilPredicateSentence(
          predStateDoor(VERB_BE),
          SilTam.indicative.withModality(MODAL_MAY)))
      parse("The door must not be open") must be equalTo(
        SilPredicateSentence(
          predStateDoor(VERB_BE),
          SilTam.indicative.negative.withModality(MODAL_MUST)))
      parse("Mustn't the door be open") must be equalTo(
        SilPredicateSentence(
          predStateDoor(VERB_BE),
          SilTam.interrogative.negative.withModality(MODAL_MUST)))
    }

    "parse existence" in
    {
      def doorExistencePred(verb : SilWord = VERB_IS) = SilStatePredicate(
        SilNounReference(NOUN_DOOR, DETERMINER_NONSPECIFIC),
        verb,
        SilExistenceState())

      parse("There is a door") must be equalTo(
        SilPredicateSentence(doorExistencePred()))
      parse("There exists a door") must be equalTo(
        SilPredicateSentence(doorExistencePred(VERB_BE)))
      parse("There is not a door") must be equalTo(
        SilPredicateSentence(
          doorExistencePred(),
          SilTam.indicative.negative))
      parse("There must be a door") must be equalTo(
        SilPredicateSentence(
          doorExistencePred(VERB_BE),
          SilTam.indicative.withModality(MODAL_MUST)))
      parse("There is a door?") must be equalTo(
        SilPredicateSentence(
          doorExistencePred(),
          SilTam.interrogative))
      parse("Is there a door") must be equalTo(
        SilPredicateSentence(
          doorExistencePred(),
          SilTam.interrogative))
      parse("Must there be a door") must be equalTo(
        SilPredicateSentence(
          doorExistencePred(VERB_BE),
          SilTam.interrogative.withModality(MODAL_MUST)))

      parse("There is a big door") must be equalTo(
        SilPredicateSentence(
          SilStatePredicate(
            SilReference.qualified(
              SilNounReference(NOUN_DOOR, DETERMINER_NONSPECIFIC),
              Seq(QUALIFIER_BIG)),
            VERB_IS,
            SilExistenceState())))

      val doorPlusWindow = Seq(
        SilNounReference(NOUN_DOOR, DETERMINER_NONSPECIFIC),
        SilNounReference(NOUN_WINDOW, DETERMINER_NONSPECIFIC))
      parse("There is a door and a window") must be equalTo(
        SilPredicateSentence(
          SilStatePredicate(
            SilConjunctiveReference(
              DETERMINER_ALL,
              doorPlusWindow),
            VERB_IS,
            SilExistenceState())))
      parse("Is there a door or a window") must be equalTo(
        SilPredicateSentence(
          SilStatePredicate(
            SilConjunctiveReference(
              DETERMINER_ANY,
              doorPlusWindow),
            VERB_IS,
            SilExistenceState()),
          SilTam.interrogative))
    }

    "parse relative clauses" in
    {
      parse("a door that is shut is closed") must be equalTo(
        SilPredicateSentence(
          SilStatePredicate(
            SilReference.qualified(
              SilNounReference(NOUN_DOOR, DETERMINER_NONSPECIFIC),
              Seq(STATE_SHUT)),
            VERB_IS,
            SilPropertyState(STATE_CLOSED))))
    }

    "give up" in
    {
      val inputUnspecified =
        "colorless green ideas slumber and fume furiously to see you"
      val result = parse(inputUnspecified)
      result.hasUnknown must beTrue
    }

    "preserve unrecognized reference syntax" in
    {
      if (!SprParser.isCoreNLP) {
        skipped("CoreNLP only")
      }
      val input = "The big nor strong door is open."
      val result = parse(input)
      result match {
        case SilPredicateSentence(
          SilStatePredicate(
            SilUnrecognizedReference(syntaxTree),
            SilStatePredefVerb(STATE_PREDEF_BE),
            SilPropertyState(STATE_OPEN),
            Seq()),
          tam,
          SilFormality.DEFAULT
        ) if (tam.isIndicative) => {
          val dependencyStripped = SprSyntaxRewriter.rewriteAbstract(
            syntaxTree,
            true)
          dependencyStripped must be equalTo(
            SptNP(
              SptDT(leafCapitalized("the")),
              SptADJP(
                SptJJ(leaf("big")),
                SptCC(leaf("nor")),
                SptJJ(leaf("strong"))
              ),
              SptNN(leaf("door"))))
        }
        case _ => {
          s"unexpected result $result" must beEmpty
        }
      }
    }

    "deal with unknowns" in
    {
      predStateDoor().hasUnknown must beFalse
      val tree = SprSyntaxLeaf("", "", "")
      SilUnrecognizedPredicate(tree).hasUnknown must beTrue
      SilPredicateSentence(predStateDoor()).hasUnknown must beFalse
      SilPredicateSentence(
        SilUnrecognizedPredicate(tree)).hasUnknown must beTrue
    }
  }
}
