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

sealed trait ShlurdPerson
case object PERSON_FIRST extends ShlurdPerson
case object PERSON_SECOND extends ShlurdPerson
case object PERSON_THIRD extends ShlurdPerson

sealed trait ShlurdGender
case object GENDER_M extends ShlurdGender
case object GENDER_F extends ShlurdGender
case object GENDER_N extends ShlurdGender

sealed trait ShlurdDeterminer
case object DETERMINER_NONE extends ShlurdDeterminer
case object DETERMINER_UNIQUE extends ShlurdDeterminer
case object DETERMINER_NONSPECIFIC extends ShlurdDeterminer
case object DETERMINER_ANY extends ShlurdDeterminer
case object DETERMINER_ALL extends ShlurdDeterminer
case object DETERMINER_UNSPECIFIED extends ShlurdDeterminer

sealed trait ShlurdLocative
case object LOC_INSIDE extends ShlurdLocative
case object LOC_OUTSIDE extends ShlurdLocative
case object LOC_AT extends ShlurdLocative
case object LOC_NEAR extends ShlurdLocative
case object LOC_ON extends ShlurdLocative
case object LOC_ABOVE extends ShlurdLocative
case object LOC_BELOW extends ShlurdLocative
case object LOC_LEFT extends ShlurdLocative
case object LOC_RIGHT extends ShlurdLocative
case object LOC_FRONT extends ShlurdLocative
case object LOC_BEHIND extends ShlurdLocative

sealed trait ShlurdCount
case object COUNT_SINGULAR extends ShlurdCount
case object COUNT_PLURAL extends ShlurdCount

sealed trait ShlurdMark
case object MARK_NONE extends ShlurdMark
case object MARK_SUBJECT extends ShlurdMark
case object MARK_DIRECT_OBJECT extends ShlurdMark