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
package com.lingeringsocket.shlurd

import net.sf.extjwnl.data._
import net.sf.extjwnl.dictionary._

import scala.collection._
import scala.collection.JavaConverters._

import java.util.regex._

object ShlurdEnglishAffixes
{
  val SUFFIX_ING = "ing"
}

object ShlurdWordnet
{
  import ShlurdEnglishAffixes._

  val dictionary = Dictionary.getDefaultResourceInstance

  val morphology = dictionary.getMorphologicalProcessor

  private val plainPattern = Pattern.compile("\\p{javaLowerCase}+")

  def getWordSenses(pos : POS, lemma : String) : Seq[Synset] =
  {
    Option(dictionary.getIndexWord(pos, lemma)) match {
      case Some(indexWord) => {
        indexWord.getSenses.asScala
      }
      case _ => Seq.empty
    }
  }

  def getVerbSenses(lemma : String) : Seq[Synset] =
  {
    getWordSenses(POS.VERB, lemma)
  }

  def getNounSenses(lemma : String) : Seq[Synset] =
  {
    getWordSenses(POS.NOUN, lemma)
  }

  def allNounSenses() =
  {
    dictionary.getSynsetIterator(POS.NOUN).asScala
  }

  def getVerbFrames(lemma : String) : Seq[String] =
  {
    getVerbSenses(lemma).flatMap(_.getVerbFrames).distinct
  }

  def getVerbFrameFlags(lemma : String) : BitSet =
  {
    getVerbSenses(lemma).map(sense => BitSet(sense.getVerbFrameIndices:_*)).
      reduceLeftOption(_ union _).getOrElse(
        BitSet.empty)
  }

  def getUsageScore(lemma : String, pos : POS) : Int =
  {
    Option(dictionary.getIndexWord(pos, lemma)) match {
      case Some(indexWord) => {
        val senseIter = indexWord.getSenses.iterator
        if (senseIter.hasNext) {
          val wordIter = senseIter.next.getWords.iterator
          if (wordIter.hasNext) {
            wordIter.next.getUseCount / 5
          } else {
            -1
          }
        } else {
          -1
        }
      }
      case _ => -1
    }
  }

  def isPotentialAdjective(inflected : String) : Boolean =
  {
    Option(dictionary.getIndexWord(POS.ADJECTIVE, inflected)) match {
      case Some(indexWord) => true
      case _ => false
    }
  }

  def isPotentialAdverb(inflected : String) : Boolean =
  {
    Option(dictionary.getIndexWord(POS.ADVERB, inflected)) match {
      case Some(indexWord) => true
      case _ => false
    }
  }

  def isPotentialNoun(inflected : String) : Boolean =
  {
    Option(dictionary.getIndexWord(POS.NOUN, inflected)) match {
      case Some(indexWord) => true
      case _ => false
    }
  }

  def isPotentialVerb(inflected : String) : Boolean =
  {
    Option(dictionary.getIndexWord(POS.VERB, inflected)) match {
      case Some(indexWord) => true
      case _ => false
    }
  }

  def isPotentialGerund(inflected : String) : Boolean =
  {
    if (!inflected.endsWith(SUFFIX_ING)) {
      false
    } else {
      Option(dictionary.getIndexWord(POS.ADJECTIVE, inflected)) match {
        case Some(indexWord) => true
        case _ => false
      }
    }
  }

  def isPotentialPlural(noun : String) : Boolean =
  {
    val bases = morphology.lookupAllBaseForms(POS.NOUN, noun).asScala
    return (bases.size > 1) || !bases.contains(noun)
  }

  def isPlural(indexWord : IndexWord) : Boolean =
  {
    val senses = indexWord.getSenses.asScala
    senses.exists(s => {
      val equivalents = s.getWords.asScala.
        filter(w => isPlainWord(w.getLemma)).
        filter(_.getLemma != indexWord.getLemma)
      s.getGloss.startsWith("(plural) ") ||
        (equivalents.count(w => isPotentialPlural(w.getLemma)) > 1)
    })
  }

  def isPlainWord(word : String) : Boolean =
  {
    plainPattern.matcher(word).matches
  }

  def isAcronym(indexWord : IndexWord) : Boolean =
  {
    indexWord.getSenses.asScala.forall(sense => {
      sense.getWords.asScala.exists(word => {
        (word.getLemma.forall(_.isUpper)) &&
          (word.getLemma.toLowerCase == indexWord.getLemma)
      })
    })
  }

  def getSenseId(synset : Synset) : String =
  {
    s"${synset.getPOS.getKey}:${synset.getOffset}"
  }

  def getSenseId(synsets : Seq[Synset]) : String =
  {
    synsets.map(getSenseId).mkString("|")
  }

  def findSense(senseId : String) : Synset =
  {
    val components = senseId.split(':')
    val pos = POS.getPOSForKey(components.head)
    dictionary.getSynsetAt(pos, components.last.toLong)
  }

  def findSenses(senseId : String) : Seq[Synset] =
  {
    if (senseId.isEmpty) {
      Seq.empty
    } else {
      senseId.split('|').map(findSense)
    }
  }

  def getGlossDefinitions(synset : Synset) : Seq[String] =
  {
    val quote = DQUOTE
    val gloss = synset.getGloss
    gloss.split(';').map(_.trim).filterNot(_.startsWith(quote))
  }

  def getGlossExamples(synset : Synset) : Seq[String] =
  {
    val quote = DQUOTE
    val gloss = synset.getGloss
    gloss.split(';').map(_.trim).filter(_.startsWith(quote)).map(
      _.stripPrefix(quote).stripSuffix(quote))
  }
}
