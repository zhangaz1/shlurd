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
      case "LCURLY" | "{" => LABEL_LCURLY
      case "RCURLY" | "}" => LABEL_RCURLY
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
    stream : Stream[Set[SprSyntaxTree]], minLength : Int = 1)
      : Map[Int, Set[SprSyntaxTree]] =
  {
    root.matchPatterns(stream, minLength)
  }

  def addRule(syntaxTree : SprSyntaxTree)
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
      List.empty)
  }

  def addRule(label : String, pattern : Seq[String])
  {
    root.addFoldedPattern(
      pattern.map(foldAndValidateLabel),
      foldAndValidateLabel(label),
      List.empty)
  }

  def addSymbol(symbol : String, patterns : Seq[Seq[String]])
  {
    symbols.put(symbol, patterns.map(_.map(foldAndValidateLabel)))
  }

  override def toString =
  {
    val sw = new StringWriter
    val pw = new PrintWriter(sw)
    root.dump(pw, 0)
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
    // NP -> DT NN
    //
    // [root]---->[]---->[NP=>false]
    //        DT     NN
    private val children = new mutable.LinkedHashMap[String, PatternVertex]

    // NP -> DT (JJ)* NN
    // XP -> DT JJ
    //
    //              []<--CYCLE
    //             JJ\    |
    //                \   |
    //                 ->[XP=>false]---->[NP=>true]
    // [root]---->[]--/              NN
    //        DT     JJ
    private val cycleChildren = new mutable.HashSet[PatternVertex]

    private val reductions = new mutable.LinkedHashMap[String, Boolean]

    private var maxPatternLength : Int = 1

    def getMaxPatternLength() : Int =
    {
      maxPatternLength
    }

    def matchPatterns(
      stream : Stream[Set[SprSyntaxTree]], minLength : Int = 1)
        : Map[Int, Set[SprSyntaxTree]] =
    {
      if (maxPatternLength >= minLength) {
        val map = new mutable.HashMap[Int, mutable.Set[SprSyntaxTree]]
        val longest = matchPatternsSub(
          stream, map, Seq.empty, minLength, false)
        if (map.isEmpty) {
          Map(longest -> Set.empty)
        } else {
          map
        }
      } else {
        Map(0 -> Set.empty)
      }
    }

    private def matchPatternsSub(
      stream : Stream[Set[SprSyntaxTree]],
      map : mutable.Map[Int, mutable.Set[SprSyntaxTree]],
      prefix : Seq[SprSyntaxTree],
      minLength : Int,
      cycle : Boolean
    ) : Int =
    {
      var longest = {
        if (prefix.size >= minLength) {
          reductions.foreach {
            case (label, allowCycle) => {
              if (allowCycle || !cycle) {
                val newTree = SprSyntaxRewriter.recompose(label, prefix)
                val resultSet = map.getOrElseUpdate(
                  prefix.size,
                  new mutable.HashSet[SprSyntaxTree]
                )
                resultSet += newTree
              }
            }
          }
          prefix.size
        } else {
          0
        }
      }
      if (stream.nonEmpty && (children.nonEmpty || cycleChildren.nonEmpty)) {
        stream.head.map(syntaxTree => {
          val label = foldLabel(syntaxTree.label)
          children.get(label).foreach(child => {
            if ((prefix.size + 1 + child.maxPatternLength) >= minLength) {
              val childLongest = child.matchPatternsSub(
                stream.tail, map, prefix :+ syntaxTree, minLength, cycle)
              if (childLongest > longest) {
                longest = childLongest
              }
            }
          })
          cycleChildren.foreach(child => {
            val cycleLongest = child.matchPatternsSub(
              stream, map, prefix, minLength, true)
            if (cycleLongest > longest) {
              longest = cycleLongest
            }
          })
        })
      }
      longest
    }

    private[parser] def addFoldedPattern(
      pattern : Seq[String],
      label : String,
      cycleLinkerStack : List[CycleLinker],
      cycle : Boolean = false)
    {
      val iOptional = pattern.indexWhere(
        Seq(ZERO_OR_ONE, ZERO_OR_MORE).contains)
      if (iOptional == -1) {
        addUnrolledPattern(pattern, label, cycleLinkerStack,
          cycle || cycleLinkerStack.nonEmpty)
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
          cycleLinkerStack,
          cycle)
        addFoldedPattern(
          pattern.take(iOptional - 1) ++ pattern.drop(iOptional + 1),
          label,
          cycleLinkerStack,
          cycle)
      }
    }

    private def addUnrolledPattern(
      pattern : Seq[String],
      label : String,
      cycleLinkerStack : List[CycleLinker],
      cycle : Boolean)
    {
      if (pattern.isEmpty) {
        assert(cycleLinkerStack.isEmpty)
        reductions.get(label) match {
          case Some(true) => {}
          case _ => {
            reductions.put(label, cycle)
          }
        }
      } else {
        val symbol = pattern.head
        val patternTail = pattern.tail
        if (symbol == CYCLE_END) {
          assert(cycleLinkerStack.nonEmpty)
          val cycleVertex = cycleLinkerStack.head.vertex
          cycleChildren += cycleVertex
          addUnrolledPattern(
            patternTail, label, cycleLinkerStack.tail,
            cycle || cycleLinkerStack.nonEmpty)
        } else {
          val (remainder, newLinkerStack, insert) = {
            patternTail.headOption match {
              case Some(ONE_OR_MORE) => {
                val cycleVertex = new PatternVertex
                tupleN((patternTail.tail,
                  CycleLinker(cycleVertex, Some(cycleVertex)) ::
                    cycleLinkerStack,
                  Seq(CYCLE_END)))
              }
              case _ => {
                tupleN((patternTail, cycleLinkerStack, Seq.empty))
              }
            }
          }
          val alternatives = symbols.get(symbol).getOrElse(Seq(Seq(symbol)))
          alternatives.foreach(alternative => {
            if (symbols.contains(alternative.head) ||
              alternative.contains(ONE_OR_MORE) ||
              alternative.contains(ZERO_OR_MORE) ||
              alternative.contains(ZERO_OR_ONE)
            ) {
              addFoldedPattern(
                alternative ++ insert ++ remainder, label, newLinkerStack,
                cycle || newLinkerStack.nonEmpty)
            } else {
              val child = children.getOrElseUpdate(alternative.head, {
                new PatternVertex
              })
              val childLinkerStack = newLinkerStack.map(linker => {
                linker match {
                  case CycleLinker(vertex, Some(firstVertex)) => {
                    firstVertex.children.put(alternative.head, child)
                    CycleLinker(vertex, None)
                  }
                  case _ => linker
                }
              })
              child.addFoldedPattern(
                alternative.tail ++ insert ++ remainder,
                label, childLinkerStack,
                cycle || childLinkerStack.nonEmpty)
            }
          })
        }
      }
      maxPatternLength = {
        if (cycleLinkerStack.nonEmpty || cycleChildren.nonEmpty) {
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

    private[parser] def dump(pw : PrintWriter, level : Int)
    {
      val prefix = "  " * level
      pw.print(prefix)
      pw.println(s"REDUCTIONS:  $reductions")
      cycleChildren.foreach(child => {
        pw.print(prefix)
        pw.println(s"CYCLE:  " + child.children.keys)
      })
      children.foreach({
        case (label, child) => {
          pw.print(prefix)
          pw.println(s"CHILD:  $label")
          child.dump(pw, level + 1)
        }
      })
    }
  }
}
