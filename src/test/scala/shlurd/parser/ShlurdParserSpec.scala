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

import org.specs2.mutable._

import ShlurdQuantifier._

class ShlurdParserSpec extends Specification
{
  private val STATE_OPEN = "open"

  private val STATE_CLOSED = "close"

  private def predDoor(
    state : String = STATE_OPEN, quantifier : ShlurdQuantifier = QUANT_ONE) =
  {
    ShlurdStatePredicate(
      ShlurdConcreteReference("door", quantifier),
      ShlurdPhysicalState(state))
  }

  "ShlurdParser" should
  {
    "parse a statement" in
    {
      val input = "the door is open"
      ShlurdParser(input).parse must be equalTo
        ShlurdPredicateStatement(predDoor())
      ShlurdParser(input + ".").parse must be equalTo
        ShlurdPredicateStatement(predDoor())
      ShlurdParser(input + "!").parse must be equalTo
        ShlurdPredicateStatement(predDoor())
      ShlurdParser(input + "?").parse must be equalTo
        ShlurdPredicateQuestion(predDoor())
    }

    "parse a question" in
    {
      val input = "is the door open"
      ShlurdParser(input).parse must be equalTo
        ShlurdPredicateQuestion(predDoor())
      ShlurdParser(input + "?").parse must be equalTo
        ShlurdPredicateQuestion(predDoor())
    }

    "parse a command" in
    {
      val input = "open the door"
      ShlurdParser(input).parse must be equalTo
        ShlurdStateChangeCommand(predDoor())
      ShlurdParser(input + ".").parse must be equalTo
        ShlurdStateChangeCommand(predDoor())
      ShlurdParser(input + "!").parse must be equalTo
        ShlurdStateChangeCommand(predDoor())
      ShlurdParser(input + "?").parse must be equalTo
        ShlurdStateChangeCommand(predDoor())
    }

    "lemmatize correctly" in
    {
      val command = "close the door"
      ShlurdParser(command).parse must be equalTo
        ShlurdStateChangeCommand(predDoor(STATE_CLOSED))
      val question = "is the door closed"
      ShlurdParser(question).parse must be equalTo
        ShlurdPredicateQuestion(predDoor(STATE_CLOSED))
    }

    "parse quantifiers" in
    {
      val inputAny = "open any door"
      ShlurdParser(inputAny).parse must be equalTo
        ShlurdStateChangeCommand(predDoor(STATE_OPEN, QUANT_ANY))
      val inputUnspecified = "open a door"
      ShlurdParser(inputUnspecified).parse must be equalTo
        ShlurdStateChangeCommand(predDoor(STATE_OPEN, QUANT_ANY))
      val inputSome = "open some door"
      ShlurdParser(inputSome).parse must be equalTo
        ShlurdStateChangeCommand(predDoor(STATE_OPEN, QUANT_ANY))
      val inputAll = "open all doors"
      ShlurdParser(inputAll).parse must be equalTo
        ShlurdStateChangeCommand(predDoor(STATE_OPEN, QUANT_ALL))
      val inputNone = "open no door"
      ShlurdParser(inputNone).parse must be equalTo
        ShlurdStateChangeCommand(predDoor(STATE_OPEN, QUANT_NONE))
    }
  }
}
