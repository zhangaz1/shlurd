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

import scala.collection._

import java.io._

import SprPennTreebankLabels._

object SprPhrasePatternMatcher
{
  val ZERO_OR_ONE = "?"

  val ZERO_OR_MORE = "*"

  val ONE_OR_MORE = "+"

  val CYCLE_END = "-"

  val CYCLE_INFINITY = 100000

  val allowableLabels = computeAllowableLabels

  def foldLabel(label : String) : String =
  {
    (label match {
      case LABEL_NNS | LABEL_NNP | LABEL_NNPS | LABEL_NNQ |
          LABEL_NNC => LABEL_NN
      case LABEL_VBP | LABEL_VBD | LABEL_VBZ | LABEL_VBC => LABEL_VB
      case LABEL_RP | LABEL_RBC => LABEL_RB
      case "," => "COMMA"
      case ";" => "SEMICOLON"
      case "PRP_POS" => LABEL_PRP_POS
      case "WP_POS" => LABEL_WP_POS
      case "LPAREN" | "(" => LABEL_LPAREN
      case "RPAREN" | ")" => LABEL_RPAREN
      case _ => label
    }).intern
  }

  private def computeAllowableLabels() : Set[String] =
  {
    SprPennTreebankLabels.getAll.map(foldLabel) ++
      Set(ZERO_OR_ONE, ZERO_OR_MORE, ONE_OR_MORE)
  }
}

class SprPhrasePatternMatcher
{
  private val symbols = new mutable.LinkedHashMap[String, Seq[Seq[String]]]

  import SprPhrasePatternMatcher._

  val root = new PatternVertex

  def getMaxPatternLength() : Int =
  {
    root.getMaxPatternLength
  }

  def matchPatterns(
    seq : Seq[Set[SprSyntaxTree]], start : Int, minLength : Int = 1)
      : Map[Int, Set[SprSyntaxTree]] =
  {
    root.matchPatterns(seq, start, minLength)
  }

  def addPattern(syntaxTree : SprSyntaxTree)
  {
    val children = syntaxTree.children.map(
      child => foldAndValidateLabel(child.label))
    val pattern = {
      if (syntaxTree.children.last.hasLabel(LABEL_DOT)) {
        children.dropRight(1)
      } else {
        children
      }
    }
    root.addFoldedPattern(
      pattern,
      foldAndValidateLabel(syntaxTree.label),
      None)
  }

  def addPattern(pattern : Seq[String], label : String)
  {
    root.addFoldedPattern(
      pattern.map(foldAndValidateLabel), foldAndValidateLabel(label), None)
  }

  def addSymbol(symbol : String, patterns : Seq[Seq[String]])
  {
    symbols.put(symbol, patterns.map(_.map(foldAndValidateLabel)))
  }

  def exportText(pw : PrintWriter)
  {
    root.exportText(pw)
  }

  override def toString =
  {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    exportText(pw)
    pw.close
    sw.toString
  }

  private def foldAndValidateLabel(label : String) : String =
  {
    val folded = foldLabel(label)
    if (!allowableLabels.contains(folded) && !symbols.contains(folded)) {
      throw new IllegalArgumentException(folded)
    }
    folded
  }

  case class CycleLinker(
    vertex : PatternVertex,
    firstVertex : Option[PatternVertex]
  )
  {
  }

  class PatternVertex
  {
    private val children = new mutable.LinkedHashMap[String, PatternVertex]

    private val labels = new mutable.LinkedHashSet[String]

    private var cycleStart : Boolean = false

    private val cycleLinks = new mutable.HashSet[PatternVertex]

    private var maxPatternLength : Int = 1

    def getMaxPatternLength() : Int =
    {
      maxPatternLength
    }

    def matchPatterns(
      seq : Seq[Set[SprSyntaxTree]], start : Int, minLength : Int = 1)
        : Map[Int, Set[SprSyntaxTree]] =
    {
      if (maxPatternLength >= minLength) {
        val map = new mutable.HashMap[Int, mutable.Set[SprSyntaxTree]]
        matchPatternsSub(seq, start, map, Seq.empty, minLength)
        map
      } else {
        Map.empty
      }
    }

    private def matchPatternsSub(
      seq : Seq[Set[SprSyntaxTree]],
      start : Int,
      map : mutable.Map[Int, mutable.Set[SprSyntaxTree]],
      prefix : Seq[SprSyntaxTree],
      minLength : Int
    )
    {
      if (prefix.size >= minLength) {
        labels.foreach(label => {
          val newTree = SprSyntaxRewriter.recompose(label, prefix)
          map.getOrElseUpdate(
            prefix.size,
            new mutable.HashSet[SprSyntaxTree]
          ) += newTree
        })
      }
      if ((start < seq.size) && children.nonEmpty) {
        seq(start).foreach(syntaxTree => {
          val label = foldLabel(syntaxTree.label)
          children.get(label).foreach(child => {
            if ((prefix.size + 1 + child.maxPatternLength) >= minLength) {
              child.matchPatternsSub(
                seq, start + 1, map, prefix :+ syntaxTree, minLength)
            }
          })
          children.get(ONE_OR_MORE).foreach(child => {
            child.matchPatternsSub(
              seq, start, map, prefix, minLength)
          })
        })
      }
    }

    private[parser] def addFoldedPattern(
      pattern : Seq[String],
      label : String,
      cycleLinker : Option[CycleLinker])
    {
      val iOptional = pattern.indexWhere(
        Seq(ZERO_OR_ONE, ZERO_OR_MORE).contains)
      if (iOptional == -1) {
        addUnrolledPattern(pattern, label, cycleLinker)
      } else {
        val isStar = pattern(iOptional) == ZERO_OR_MORE
        val infix = {
          if (isStar) {
            Seq(ONE_OR_MORE)
          } else {
            Seq.empty
          }
        }
        addFoldedPattern(
          pattern.take(iOptional) ++ infix ++ pattern.drop(iOptional + 1),
          label,
          cycleLinker)
        addFoldedPattern(
          pattern.take(iOptional - 1) ++ pattern.drop(iOptional + 1),
          label,
          cycleLinker)
      }
    }

    private def addUnrolledPattern(
      pattern : Seq[String],
      label : String,
      cycleLinker : Option[CycleLinker])
    {
      if (pattern.isEmpty) {
        labels += label
      } else {
        val symbol = pattern.head
        val patternTail = pattern.tail
        if (symbol == CYCLE_END) {
          assert(cycleLinker.nonEmpty)
          val cycleVertex = cycleLinker.get.vertex
          assert(!children.contains(ONE_OR_MORE))
          cycleVertex.cycleLinks += this
          children.put(ONE_OR_MORE, cycleVertex)
          addUnrolledPattern(patternTail, label, None)
        } else {
          val (remainder, newLinker, insert) = patternTail.headOption match {
            case Some(ONE_OR_MORE) => {
              assert(cycleLinker.isEmpty)
              val cycleVertex = new PatternVertex
              tupleN((patternTail.tail,
                Some(CycleLinker(cycleVertex, Some(cycleVertex))),
                Seq(CYCLE_END)))
            }
            case _ => {
              tupleN((patternTail, cycleLinker, Seq.empty))
            }
          }
          val alternatives = symbols.get(symbol).getOrElse(Seq(Seq(symbol)))
          alternatives.foreach(alternative => {
            if (symbols.contains(alternative.head) ||
              alternative.contains(ONE_OR_MORE)
            ) {
              addFoldedPattern(
                alternative ++ insert ++ remainder, label, newLinker)
            } else {
              val child = children.getOrElseUpdate(alternative.head, {
                new PatternVertex
              })
              val childLinker = newLinker match {
                case Some(CycleLinker(vertex, Some(firstVertex))) => {
                  child.cycleStart = true
                  firstVertex.children.put(alternative.head, child)
                  Some(CycleLinker(vertex, None))
                }
                case _ => newLinker
              }
              child.addFoldedPattern(
                alternative.tail ++ insert ++ remainder, label, childLinker)
            }
          })
        }
      }
      maxPatternLength = {
        if (cycleLinker.nonEmpty) {
          CYCLE_INFINITY
        } else {
          if (children.isEmpty) {
            1
          } else {
            val childMax = children.values.map(_.maxPatternLength).max
            if (childMax == CYCLE_INFINITY) {
              CYCLE_INFINITY
            } else {
              1 + childMax
            }
          }
        }
      }
    }

    private def dump(pw : PrintWriter, level : Int)
    {
      val prefix = "  " * level
      pw.print(prefix)
      pw.println(s"LABELS:  $labels")
      if (cycleLinks.isEmpty) {
        children.foreach({
          case (label, child) => {
            pw.print(prefix)
            if (child.cycleLinks.contains(this)) {
              pw.println(s"CYCLE:  " + child.children.keys)
            } else {
              pw.println(s"CHILD:  $label")
              child.dump(pw, level + 1)
            }
          }
        })
      }
    }

    def exportText(pw : PrintWriter, prefix : String = "")
    {
      labels.foreach(label => {
        pw.println(s"$label -> $prefix")
      })
      val anyCycle = children.keySet.contains(ONE_OR_MORE)
      children.foreach {
        case (label, child) => {
          if (!child.cycleLinks.contains(this)) {
            if (child.cycleStart) {
              child.exportText(pw, s"$prefix ($label")
            } else if (anyCycle) {
              child.exportText(pw, s"$prefix)+ $label")
            } else {
              child.exportText(pw, s"$prefix $label")
            }
          }
        }
      }
    }
  }
}
