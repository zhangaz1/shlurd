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

sealed trait ShlurdSentence
{
}

sealed trait ShlurdCommand extends ShlurdSentence
{
}

sealed trait ShlurdQuestion extends ShlurdSentence
{
}

sealed trait ShlurdStatement extends ShlurdSentence
{
}

sealed trait ShlurdPredicate
{
}

sealed trait ShlurdEvent
{
}

sealed trait ShlurdReference
{
}

sealed trait ShlurdState
{
}

case object ShlurdUnknownSentence extends ShlurdSentence
{
}

case object ShlurdUnknownReference extends ShlurdReference
{
}

case object ShlurdUnknownPredicate extends ShlurdPredicate
{
}

case object ShlurdUnknownState extends ShlurdState
{
}

case class ShlurdStatePredicate(
  subject : ShlurdReference,
  state : ShlurdState
) extends ShlurdPredicate
{
}

case class ShlurdStateChangeEvent(
  predicate : ShlurdStatePredicate
) extends ShlurdEvent
{
}

case class ShlurdStateChangeCommand(
  predicate : ShlurdStatePredicate
) extends ShlurdCommand
{
}

case class ShlurdPredicateQuestion(
  predicate : ShlurdPredicate
) extends ShlurdQuestion
{
}

case class ShlurdPredicateStatement(
  predicate : ShlurdPredicate
) extends ShlurdStatement
{
}

case class ShlurdQualifiedReference(
  reference : ShlurdReference,
  qualifiers : Seq[ShlurdWord]
) extends ShlurdReference
{
}

case class ShlurdPronounReference(
  person : ShlurdPerson,
  gender : ShlurdGender,
  count : ShlurdCount,
  reference : ShlurdReference = ShlurdUnknownReference
) extends ShlurdReference
{
}

case class ShlurdEntityReference(
  entity : ShlurdWord,
  quantifier : ShlurdQuantifier = QUANT_ANY,
  count : ShlurdCount = COUNT_SINGULAR
) extends ShlurdReference
{
}

case class ShlurdPropertyState(
  state : ShlurdWord
) extends ShlurdState
{
}

case class ShlurdLocationState(
  locative : ShlurdLocative,
  location : ShlurdReference
) extends ShlurdState
{
}

case class ShlurdWord(
  inflected : String,
  lemma : String)
{
}
