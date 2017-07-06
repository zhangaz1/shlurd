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
package shlurd.print

import org.specs2.mutable._

import shlurd.parser._

class ShlurdSentencePrinterSpec extends Specification
{
  private val printer = new ShlurdSentencePrinter

  private def normalize(s : String) =
  {
    val parsed = ShlurdParser(s).parseOne
    val normalized = parsed match {
      case ShlurdPredicateSentence(predicate, mood, formality) => {
        mood match {
          case MOOD_IMPERATIVE => ShlurdPredicateSentence(
            predicate, mood,
            formality.copy(force = FORCE_EXCLAMATION))
          case _ => parsed
        }
      }
      case ShlurdStateChangeCommand(predicate, formality) => {
        ShlurdStateChangeCommand(
          predicate,
          formality.copy(force = FORCE_EXCLAMATION))
      }
      case ShlurdUnknownSentence => parsed
    }
    printer.print(normalized)
  }

  private def expectPreserved(s : String) =
  {
    normalize(s) must be equalTo s
  }

  private def expectStatement(s : String) =
  {
    normalize(s) must be equalTo (s + ".")
  }

  private def expectCommand(s : String) =
  {
    normalize(s) must be equalTo (s + "!")
  }

  private def expectQuestion(s : String) =
  {
    normalize(s) must be equalTo (s + "?")
  }

  "ShlurdSentencePrinter" should
  {
    "preserve sentences" in
    {
      expectPreserved("the door is closed.")
      expectPreserved("the door is closed!")
      expectPreserved("is the door closed?")
    }

    "normalize sentences" in
    {
      expectStatement("the door is closed")
      expectQuestion("is the door closed")
      expectCommand("close the door")
      expectStatement("the chickens are fat")
      expectStatement("I am hungry")
      expectStatement("we are hungry")
      expectStatement("you are hungry")
      expectStatement("he is hungry")
      expectStatement("they are hungry")
      expectCommand("erase them")
      expectQuestion("is his granddaughter at home")
      // FIXME:  either/both etc don't work here
      expectQuestion("is franny or zooey speaking")
      expectQuestion("are franny and zooey speaking")
      expectQuestion("are franny, zooey, and phoebe speaking")
      expectQuestion("are franny, zooey and phoebe speaking")
      expectQuestion("is the server up and running")
      expectQuestion("is the server down or failing")
      expectStatement("the horse is healthy, strong, and hungry")
      expectStatement("your friend and I are hungry")
      expectStatement("your friend, Stalin, and I are hungry")
      expectStatement("the red pig, Stalin, and I are hungry")
    }
  }
}
