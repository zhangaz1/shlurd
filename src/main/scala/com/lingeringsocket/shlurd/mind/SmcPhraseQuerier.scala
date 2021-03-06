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
package com.lingeringsocket.shlurd.mind

import com.lingeringsocket.shlurd.ilang._

object SmcPhraseQuerier
{
  def containsWildcard(
    phrase : SilPhrase,
    includeConjunctions : Boolean = false,
    rejectGenitives : Boolean = false,
    includeAll : Boolean = true) : Boolean =
  {
    val querier = new SilPhraseQuerier
    var wildcard = false
    var rejected = false
    def matchWildcard = querier.queryMatcher {
      case _ : SilGenitiveReference if (rejectGenitives) => {
        // FIXME this should probably apply to
        // adpositional references as well
        rejected = true
      }
      case SilConjunctiveReference(
        DETERMINER_ANY | DETERMINER_ALL,
        _,
        _
      ) => {
        if (includeConjunctions) {
          wildcard = true
        }
      }
      case SilDeterminedReference(
        _ : SilNounReference,
        _ : SilUnlimitedDeterminer
      ) => {
        wildcard = true
      }
      case SilDeterminedReference(
        _ : SilNounReference,
        DETERMINER_ALL
      ) if (includeAll) => {
        wildcard = true
      }
    }
    querier.query(matchWildcard, phrase)
    if (rejected) {
      wildcard = false
    }
    wildcard
  }

  def containsVariable(phrase : SilPhrase) : Boolean =
  {
    var variable = false
    val querier = new SilPhraseQuerier
    def matchVariable = querier.queryMatcher {
      case SilDeterminedReference(
        _,
        DETERMINER_VARIABLE
      ) => {
        variable = true
      }
    }
    querier.query(matchVariable, phrase)
    variable
  }
}
