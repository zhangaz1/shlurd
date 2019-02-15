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

import com.lingeringsocket.shlurd.mind._

import org.specs2.mutable._
import org.specs2.specification._

object SpcWordnetSpec
{
  private val cosmos = new SpcCosmos
  // FIXME:  speed this up
  // SpcPrimordial.initCosmos(cosmos)
  private val wordnet = new SpcWordnet(cosmos)
  wordnet.loadAll

  def getCosmos() = cosmos
}

class SpcWordnetSpec extends Specification
{
  abstract class InterpreterContext extends Scope
  {
    protected val cosmos = SpcWordnetSpec.getCosmos
    protected val mind = new SpcWordnetMind(cosmos)
    protected val interpreter =
      new SpcInterpreter(
        mind, ACCEPT_NEW_BELIEFS, SmcResponseParams())

    protected def interpretBelief(input : String) =
    {
      interpret(input, "OK.")
    }

    protected def interpret(input : String, expected : String) =
    {
      val sentence = interpreter.newParser(input).parseOne
      s"pass:  $input" ==> (
        interpreter.interpret(sentence, input) === expected)
    }
  }

  "SpcWordnet" should
  {
    "load forms" in
    {
      val cosmos = SpcWordnetSpec.getCosmos
      val dogOpt = cosmos.resolveForm("wn-dog-1")
      dogOpt must beSome
      val dogForm = dogOpt.get
      val androsOpt = cosmos.resolveForm("wn-man-1")
      androsOpt must beSome
      val androsForm = androsOpt.get
      val anthroposOpt = cosmos.resolveForm("wn-man-4")
      anthroposOpt must beSome.which(_ != androsForm)
      val anthroposForm = anthroposOpt.get
      cosmos.resolveForm("wn-human-1") must beSome.which(_ == anthroposForm)
      cosmos.resolveForm("wn-xyzzy-1") must beEmpty
      val puppyOpt = cosmos.resolveForm("wn-puppy-1")
      puppyOpt must beSome
      val puppyForm = puppyOpt.get
      val graph = cosmos.getGraph
      graph.isHyponym(puppyForm, dogForm) must beTrue
      graph.isHyponym(dogForm, puppyForm) must beFalse
      graph.isHyponym(puppyForm, anthroposForm) must beFalse
    }

    "provide ontology to parser" in new InterpreterContext
    {
      interpret(
        "which animals are there",
        "There are no animals.")
      interpretBelief("a pokemon is a kind of animal")
      interpretBelief("Pikachu is a pokemon")
      interpret(
        "which animals are there",
        "There is Pikachu.")
      interpret(
        "which organisms are there",
        "There is Pikachu.")
    }
  }
}
