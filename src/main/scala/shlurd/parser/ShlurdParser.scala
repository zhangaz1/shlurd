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

import edu.stanford.nlp.simple._
import edu.stanford.nlp.trees._

import scala.collection.JavaConverters._

object ShlurdParser extends App
{
  run()

  private def dump(sentence : Sentence)
  {
    val tree = sentence.parse
    println("TREE = " + tree)
    Range(0, sentence.length).foreach(i => {
      println("DEP = " + sentence.incomingDependencyLabel(i))
    })
  }

  private def hasLabel(tree : Tree, label : String) : Boolean =
  {
    tree.label.value == label
  }

  private def expectRoot(tree : Tree) =
  {
    if (hasLabel(tree, "ROOT")) {
      assert(tree.numChildren == 1)
      expectSentence(tree.firstChild)
    } else {
      ShlurdUnknownSentence
    }
  }

  private def expectSentence(tree : Tree) =
  {
    if (hasLabel(tree, "S")) {
      if (tree.numChildren != 2) {
        ShlurdUnknownSentence
      } else {
        val np = tree.firstChild
        val vp = tree.lastChild
        if ((hasLabel(np, "NP")) && (hasLabel(vp, "VP"))) {
          expectStatement(np, vp)
        } else {
          ShlurdUnknownSentence
        }
      }
    } else {
      ShlurdUnknownSentence
    }
  }

  private def expectStatement(np : Tree, vp : Tree) =
  {
    val verb = vp.firstChild
    if (hasLabel(verb, "VBZ")) {
      ShlurdPredicateStatement(
        expectPredicate(np, vp.lastChild))
    } else {
      ShlurdUnknownSentence
    }
  }

  private def expectSubject(np : Tree) =
  {
    if (np.numChildren == 2) {
      val determiner = np.firstChild
      val nounHead = np.lastChild
      if (hasLabel(determiner, "DT")) {
        if (hasLabel(nounHead, "NN")) {
          val noun = nounHead.firstChild
          if (hasLabel(noun, "door")) {
            ShlurdFrontDoor
          } else {
            ShlurdUnknownSubject
          }
        } else {
          ShlurdUnknownSubject
        }
      } else {
        ShlurdUnknownSubject
      }
    } else {
      ShlurdUnknownSubject
    }
  }

  private def expectState(ap : Tree) =
  {
    if (hasLabel(ap, "JJ") && ap.isPreTerminal) {
      if (hasLabel(ap.firstChild, "open")) {
        ShlurdDoorIsOpen
      } else if (hasLabel(ap.firstChild, "closed")) {
        ShlurdDoorIsClosed
      } else {
        ShlurdUnknownState
      }
    } else {
      ShlurdUnknownState
    }
  }

  private def expectPredicate(np : Tree, complement : Tree) =
  {
    if (hasLabel(complement, "ADJP")) {
      val subject = expectSubject(np)
      val state = expectState(complement.firstChild)
      ShlurdStatePredicate(subject,state)
    } else {
      ShlurdUnknownPredicate
    }
  }

  def parse(input : String) : ShlurdSentence =
  {
    val doc = new Document(input)
    val sentences = doc.sentences.asScala
    assert(sentences.size == 1)
    val sentence = sentences.head
    dump(sentence)
    expectRoot(sentence.parse)
  }

  def run()
  {
    val input = "the door is open"
    parse(input)
  }
}
