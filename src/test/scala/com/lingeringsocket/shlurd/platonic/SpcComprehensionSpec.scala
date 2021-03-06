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

import com.lingeringsocket.shlurd._
import com.lingeringsocket.shlurd.mind._

import scala.util._

class SpcComprehensionSpec extends SpcResponseSpecification
{
  private val states = Map(
    "alarm service service_on_off" -> "on",
    "multimedia service service_on_off" -> "off",
    "jackphone presence presence_on_off" -> "on",
    "jillphone presence presence_on_off" -> "off",
    "casperphone presence presence_on_off" -> "on",
    "yodaphone presence presence_on_off" -> "off",
    "stove stove_on_off" -> "off",
    "stove stove_hot_cold" -> "hot",
    "lusitania boat boat_cruise_sink" -> "sink",
    "lusitania boat vehicle_move_stop" -> "move",
    "herbie car vehicle_move_stop" -> "stop"
  )

  override protected def tryEvaluateEntityProperty(
    cosmos : SpcCosmos,
    entity : SpcEntity,
    propertyName : String,
    specific : Boolean) : Try[(Option[SpcProperty], Option[String])] =
  {
    val qualifiedName =
      (entity.qualifiers.toSeq :+ entity.form.name
        :+ propertyName).mkString(" ")
    states.get(qualifiedName) match {
      case Some(state) => Success((
        cosmos.findProperty(entity.form, propertyName), Some(state)))
      case _ => super.tryEvaluateEntityProperty(
        cosmos, entity, propertyName, specific)
    }
  }

  "allow pronouns to be avoided" in new ResponderContext(
    ACCEPT_NO_BELIEFS,
    SmcResponseParams(thirdPersonPronouns = false))
  {
    loadBeliefs("/ontologies/stove.txt")
    process("is the stove hot?",
      "Yes, the stove is hot.")
  }

  "infer form from role" in new ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    processBelief("a person's lawyer must be a weasel")
    processBelief("Donald is a person")
    processBelief("Michael is Donald's lawyer")
    processTerse("is Michael a weasel", "Yes.")

    cosmos.sanityCheck must beTrue
  }

  "understand people" in new ResponderContext
  {
    loadBeliefs("/ontologies/person.txt")
    loadBeliefs("/ontologies/people.txt")
    processTerse(
      "who is Amanda's sibling",
      "Todd.")
    processTerse(
      "who is Amanda's brother",
      "Todd.")
    processTerse(
      "who is Todd's sister",
      "Amanda.")
    processTerse(
      "who is Todd's sibling",
      "Amanda.")
    processMatrix(
      "is Todd Dirk's friend",
      "Yes, he is Dirk's friend.",
      "Yes, Todd is Dirk's friend.",
      "Yes.",
      "Yes, he is.")
    processMatrix(
      "is Dirk Todd's friend",
      "Yes, he is Todd's friend.",
      "Yes, Dirk is Todd's friend.",
      "Yes.",
      "Yes, he is.")
    processMatrix(
      "is Amanda Todd's sister",
      "Yes, she is his sister.",
      "Yes, Amanda is Todd's sister.",
      "Yes.",
      "Yes, she is.")
    processMatrix(
      "is Amanda Todd's sibling",
      "Yes, she is his sibling.",
      "Yes, Amanda is Todd's sibling.",
      "Yes.",
      "Yes, she is.")
    processMatrix(
      "is Amanda Todd's brother",
      "No, she is not his brother.",
      "No, Amanda is not Todd's brother.",
      "No.",
      "No, she is not.")
    processMatrix(
      "is Dirk Todd's sister",
      "No, he is not Todd's sister.",
      "No, Dirk is not Todd's sister.",
      "No.",
      "No, he is not.")
    processMatrix(
      "is Todd Amanda's brother",
      "Yes, he is her brother.",
      "Yes, Todd is Amanda's brother.",
      "Yes.",
      "Yes, he is.")
    processMatrix(
      "is Amanda Todd's friend",
      "No, she is not his friend.",
      "No, Amanda is not Todd's friend.",
      "No.",
      "No, she is not.")
    // FIXME:  should clarify that Dirk actually has more than one friend
    processMatrix(
      "does Dirk have a friend",
      "Yes, he has a friend.",
      "Yes, Dirk has a friend.",
      "Yes.",
      "Yes, he does.")
    processMatrix(
      "does Dirk have any friends",
      "Yes, he has two of them.",
      "Yes, Dirk has two of them.",
      "Yes.",
      "Yes, he does.")
    processMatrix(
      "does Todd have friends",
      "Yes, he has friends.",
      "Yes, Todd has friends.",
      "Yes.",
      "Yes, he does.")
    processMatrix(
      "does Todd have any friends",
      "Yes, he has one of them.",
      "Yes, Todd has one of them.",
      "Yes.",
      "Yes, he does.")
    processMatrix(
      "does Amanda have friends",
      "No, she does not have friends.",
      "No, Amanda does not have friends.",
      "No.",
      "No, she does not.")
    processMatrix(
      "does Amanda have a friend",
      "No, she does not have a friend.",
      "No, Amanda does not have a friend.",
      "No.",
      "No, she does not.")
    processMatrix(
      "does Amanda have any friends",
      "No, she has no friends.",
      "No, Amanda has no friends.",
      "No.",
      "No, she does not.")
    processMatrix(
      "who is Todd",
      "He is Amanda's brother.",
      "Todd is Amanda's brother.",
      "Amanda's brother.",
      "Amanda's brother.")
    processMatrix(
      "who is Bart",
      "She is Rapunzel's owner.",
      "Bart is Rapunzel's owner.",
      "Rapunzel's owner.")
    processMatrix(
      "who is Todd's friend",
      "His friend is Dirk.",
      "Todd's friend is Dirk.",
      "Dirk.")
    processMatrix(
      "who are Todd's friends",
      "His friend is Dirk.",
      "Todd's friend is Dirk.",
      "Dirk.")
    processMatrix(
      "which person is Todd's friend",
      "His friend is Dirk.",
      "Todd's friend is Dirk.",
      "Dirk.")
    processMatrix(
      "who is Dirk's friend",
      "His friends are Todd and Bart.",
      "Dirk's friends are Todd and Bart.",
      "Todd and Bart.")
    processMatrix(
      "who is Amanda's friend",
      "No one is her friend.",
      "No one is Amanda's friend.",
      "No one.")
    processMatrix(
      "who are Amanda's friends",
      "No one is her friend.",
      "No one is Amanda's friend.",
      "No one.")
    processExceptionExpected(
      "who has Amanda's friend",
      "But I don't know about any such friend.",
      ShlurdExceptionCode.NonExistent)
    processExceptionExpected(
      "is Ford Todd's friend",
      "Sorry, I don't know about any 'Ford'.",
      ShlurdExceptionCode.UnknownForm)
    processExceptionExpected(
      "is Todd Ford's friend",
      "Sorry, I don't know about any 'Ford'.",
      ShlurdExceptionCode.UnknownForm)
    // FIXME:  should clarify that they are not necessarily
    // friends OF EACH OTHER
    process(
      "who is a friend",
      "Dirk, Todd, and Bart are friends.")
    processMatrix(
      "is Amanda a friend",
      "No, she is not a friend.",
      "No, Amanda is not a friend.",
      "No.",
      "No, she is not.")
    processMatrix(
      "is Amanda a brother",
      "No, she is not a brother.",
      "No, Amanda is not a brother.",
      "No.",
      "No, she is not.")
    processMatrix(
      "is Amanda a dog",
      "No, she is not a dog.",
      "No, Amanda is not a dog.",
      "No.",
      "No, she is not.")
    processMatrix(
      "is Amanda an owner",
      "No, she is not an owner.",
      "No, Amanda is not an owner.",
      "No.",
      "No, she is not.")
    processMatrix(
      "is Amanda a groomer",
      "No, she is not a groomer.",
      "No, Amanda is not a groomer.",
      "No.",
      "No, she is not.")
    processMatrix(
      "is Rapunzel a dog",
      "Yes, it is a dog.",
      "Yes, Rapunzel is a dog.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is Bart an owner",
      "Yes, she is an owner.",
      "Yes, Bart is an owner.",
      "Yes.",
      "Yes, she is.")
    processExceptionExpected(
      "is Amanda a robot",
      "Sorry, I don't know about any 'robot'.",
      ShlurdExceptionCode.UnknownForm)
    process(
      "who is a person",
      "Scott, Dirk, Todd, Hugo, Arthur, Amanda, and Bart are persons.")
    process(
      "who is a man",
      "Dirk, Todd, Hugo, and Arthur are men.")
    process(
      "who is a brother",
      "Todd is a brother.")
    // FIXME have to use BLACKWING because Blackwing gets parsed
    // as -ing verb, heh
    processMatrix(
      "is BLACKWING an organization",
      "Yes, it is an organization.",
      "Yes, BLACKWING is an organization.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is BLACKWING a conspiracy",
      "No, it is not a conspiracy.",
      "No, BLACKWING is not a conspiracy.",
      "No.",
      "No, it is not.")
    process(
      "who has an uncle",
      "No one has an uncle.")
    process(
      "which person has an uncle",
      "No person has an uncle.")
    process(
      "who has a friend",
      "Dirk and Todd have a friend.")
    process(
      "who has friends",
      "Dirk and Todd have friends.")
    processExceptionExpected(
      "who is Ford",
      "Sorry, I don't know about any 'Ford'.",
      ShlurdExceptionCode.UnknownForm)
    processMatrix(
      "who is Hugo",
      "He is one of BLACKWING's operatives.",
      "Hugo is one of BLACKWING's operatives.",
      "One of BLACKWING's operatives.")
    processMatrix(
      "who is Arthur",
      "He is a man.",
      "Arthur is a man.",
      "A man.")
    processMatrix(
      "is BLACKWING Hugo's employer",
      "Yes, it is his employer.",
      "Yes, BLACKWING is Hugo's employer.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "which organization is Hugo's employer",
      "His employer is BLACKWING.",
      "Hugo's employer is BLACKWING.",
      "BLACKWING.")
    processMatrix(
      "is BLACKWING Todd's employer",
      "No, it is not his employer.",
      "No, BLACKWING is not Todd's employer.",
      "No.",
      "No, it is not.")
    processMatrix(
      "which organization is Todd's employer",
      "No organization is his employer.",
      "No organization is Todd's employer.",
      "No organization.")
  }

  "understand relatives" in new ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    loadBeliefs("/ontologies/person.txt")
    loadBeliefs("/ontologies/relatives.txt")
    process("who is Henry", "He is Titus' uncle.")
    process("who is Marion's aunt", "Her aunt is Laura.")
    process("who is the aunt of Marion", "Her aunt is Laura.")
    process("who is Marion's auntie", "Her auntie is Laura.")
    process("is Laura an aunt", "Yes, she is an aunt.")
    process("is Laura an auntie", "Yes, she is an auntie.")
    process("who is Laura's niece", "Her nieces are Fancy and Marion.")
    process("Fancy is Laura's nephew?", "No, she is not Laura's nephew.")
    process("is Everard a person?", "I don't know.")
    process("does Laura have a godmother", "Yes, she has a godmother.")
    process("who is Laura's godmother", "I don't know.")
    process("Marion is Laura's godmother?",
      "No, she is not Laura's godmother.")
    process("Fancy is Laura's godmother?",
      "No, she is not Laura's godmother.")
    process("Henry is Laura's godmother?",
      "No, he is not her godmother.")
    process("does Laura have a godfather",
      "No, she does not have a godfather.")
    processBelief("Fancy is Laura's godmother")
    processBelief("Titus is Laura's wise guy")
    process("who is Laura's godmother",
      "Her godmother is Fancy.")
    process("who is Laura's godfather",
      "Her godfather is Titus.")
    process("Marion is Laura's godmother?",
      "No, she is not Laura's godmother.")
    process("Fancy is Laura's godmother?",
      "Yes, she is Laura's godmother.")
    process("does Laura have a wise guy",
      "Yes, she has a wise guy.")
    process("who is Henry's cleaning lady",
      "His cleaning ladies are Marion and Fancy.")
    process("which person's godmother is Fancy",
      "She is Laura's godmother.")
    process("whose godmother is Fancy",
      "She is Laura's godmother.")
    process("whose mindset is silly",
      "Fancy's mindset is silly.")

    cosmos.sanityCheck must beTrue
  }

  "understand locations" in new ResponderContext
  {
    loadBeliefs("/ontologies/containment.txt")
    loadBeliefs("/ontologies/person.txt")
    loadBeliefs("/ontologies/location.txt")

    processMatrix(
      "where is Janet",
      "She is in Christine.",
      "Janet is in Christine.",
      "Christine.",
      "Christine.")
    processTerse("where is Ubuntu", "Nowhere.")
    processTerse("where is Herbie", "I don't know.")
    processTerse("where is Christine", "I don't know.")
    processTerse("where is Chrissy", "Christine.")
    processTerse("where is Janet", "Christine.")
    processTerse("is Jack in Herbie", "Yes.")
    processTerse("is Jack in Christine", "No.")
    processTerse("is Chrissy in Herbie", "No.")
    processTerse("is Chrissy in Christine", "Yes.")
    processTerse("is Janet in Herbie", "No.")
    processTerse("is Janet in Christine", "Yes.")
    processTerse("who is in Herbie", "Jack.")
    processTerse("who is in Christine", "Chrissy and Janet.")
    processTerse("how many men are in Herbie", "One of them.")
    processTerse("how many women are in Herbie", "No women.")
    processTerse("how many men are in Christine", "No men.")
    processTerse("how many women are in Christine", "Two of them.")
    processTerse(
      "how many men are Furley's tenants",
      "Two of them.")
    processTerse(
      "how many men in Herbie are Furley's tenants",
      "One of them.")
    processTerse(
      "how many women in Herbie are Furley's tenants",
      "No women in Herbie.")
    processExceptionExpected(
      "where is the helicopter",
      "But I don't know about any such helicopter.",
      ShlurdExceptionCode.NonExistent)
    processMatrix(
      "who is in KITT",
      "No one is in it.",
      "No one is in KITT.",
      "No one.",
      "No one.")
    cosmos.sanityCheck must beTrue
  }

  "understand inverse associations" in new
    ResponderContext(ACCEPT_MODIFIED_BELIEFS)
  {
    processBelief("a person is a kind of spc-someone")
    processBelief("a person's professor must be a person")
    processBelief("a person's student must be a person")
    processBelief("a person may have students")
    processBelief("a person may have a professor")

    processBelief("if one person is another person's student, " +
      "then equivalently the second person is the first person's professor")
    processBelief("Eugene is a person")
    processBelief("Jerold is a person")
    processBelief("John is a person")
    processBelief("Erik is a person")
    processBelief("Eugene is John's professor")
    processBelief("Eugene is Erik's professor")
    processBelief("Jerold is Erik's professor")
    processTerse("who are Eugene's students", "John.")
    processBelief("John has no professor")
    processTerse("who is John's professor", "No one.")
    processTerse("who are Eugene's students", "No one.")
  }

  "understand implicit inverse associations" in new ResponderContext(
    ACCEPT_MODIFIED_BELIEFS)
  {
    processBelief("A map-place is a kind of object.")
    processBelief("A map-place's map-neighbor must be a map-place.")
    processBelief("If a map-place is another map-place's map-neighbor, " +
      "then equivalently the other map-place is " +
      "the first map-place's map-neighbor.")
    processBelief("A bedroom is a kind of map-place.")
    processBelief("There is a bedroom.")
    processBelief("A bathroom is a kind of map-place.")
    processBelief("There is a bathroom.")
    processBelief("The bedroom is the bathroom's map-neighbor.")

    processTerse("What is the bathroom's map-neighbor?", "The bedroom.")
    processTerse("What is the bedroom's map-neighbor?", "The bathroom.")

    cosmos.sanityCheck must beTrue
  }

  "understand state specified genitives" in new
    ResponderContext(ACCEPT_MODIFIED_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("a person's cousin must be a person")
    processBelief("Curtis is a person")
    processBelief("Andrew is a person")
    processBelief("Andrea is a person")
    processBelief("Andrew is Curtis' cousin")
    processBelief("Andrea is Curtis' cousin")
    processBelief("the house is an object")
    processBelief("the garden is an object")
    processBelief("the gazebo is an object")
    processBelief("the gazebo is in the garden")
    processBelief("Andrew is in the house")
    processBelief("Andrea is in the garden")
    processTerse(
      "who is {Curtis' cousin in the house}",
      "Andrew.")
    processTerse(
      "who is {Curtis' cousin in the gazebo's container}",
      "Andrea.")
    processTerse(
      "who are {Curtis' cousins in the gazebo's containers}",
      "Andrea.")
  }

  "understand indirect objects" in new
    ResponderContext(ACCEPT_MODIFIED_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("if a person gives an object " +
      "to another person (the recipient), " +
      "then the object becomes the recipient's contained-object")
    processBelief("if a person passes an object " +
      "to another person (the recipient), " +
      "then the person gives the object to the recipient")

    processBelief("Curtis is a person")
    processBelief("Andrew is a person")
    processBelief("the bomb is an object")
    process("where is the bomb", "I don't know.")
    processBelief("Curtis gives Andrew the bomb")
    processTerse("where is the bomb", "Andrew.")
    processBelief("Andrew passes the bomb to Curtis")
    processTerse("where is the bomb", "Curtis.")
  }

  "understand genitives in beliefs" in new
    ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("the wrench is an object")
    processBelief("the screwdriver is an object")
    processBelief("the engine is an object")
    processBelief("the wrench is Mason's possession")
    processBelief("the screwdriver is Mason's possession")
    processBelief("the engine's contained-objects are Mason's possessions")
    processTerse("which objects are in the engine",
      "The wrench and the screwdriver.")
  }

  "understand epsilon beliefs" in new
    ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("a person may have possessions")
    processBelief("the engine is an object")
    processBelief("Mason is a person")
    processBelief("the engine's contained-object is Mason's possession")
    processTerse("which objects are in the engine", "No objects.")
  }

  "understand compound subject references" in new
    ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("the engine is an object")
    processBelief("the wrench is an object")
    processBelief("the screwdriver is an object")
    processBelief("the saw is an object")
    processBelief("Edgar is a person")
    processBelief("the wrench is Edgar's possession")
    processBelief("the screwdriver is Edgar's possession")
    processBelief("Edgar's possessions are in the engine")
    processTerse("which objects are in the engine",
      "The wrench and the screwdriver.")
  }

  "understand unique determiner in genitive" in new
    ResponderContext(ACCEPT_MODIFIED_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("the engine is an object")
    processBelief("the box is an object")
    processBelief("the box is in the engine")
    processBelief("the wrench is an object")
    processBelief("the wrench's container is the box's container")
    processTerse("which objects are in the engine",
      "The box and the wrench.")
  }

  "understand negatives" in new
    ResponderContext(ACCEPT_MODIFIED_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("the wrench is an object")
    processBelief("the hammer is an object")
    processBelief("the screwdriver is an object")
    processBelief("the box is an object")
    processBelief("the wrench's container is the box")
    processBelief("the hammer and the screwdriver are in the box")
    processTerse("which objects are in the box",
      "The wrench, the hammer, and the screwdriver.")
    processBelief("the wrench's container is not the box")
    processTerse("which objects are in the box",
      "The hammer and the screwdriver.")
    processBelief("the hammer is no longer in the box")
    processTerse("which objects are in the box", "The screwdriver.")
    processBelief("the screwdriver is not in the box")
    processTerse("which objects are in the box", "No objects.")
  }

  "understand taxonomy" in new ResponderContext
  {
    loadBeliefs("/ontologies/person.txt")
    loadBeliefs("/ontologies/vehicles.txt")

    process("is Herbie moving", "No, he is not moving.")

    processMatrix(
      "is Herbie moving",
      "No, he is not moving.",
      "No, Herbie is not moving.",
      "No.",
      "No, he is not.")
    processMatrix(
      "is Herbie stopped",
      "Yes, he is stopped.",
      "Yes, Herbie is stopped.",
      "Yes.",
      "Yes, he is.")
    processMatrix(
      "is Lusitania moving",
      "Yes, it is moving.",
      "Yes, Lusitania is moving.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is Lusitania stopped",
      "No, it is not stopped.",
      "No, Lusitania is not stopped.",
      "No.",
      "No, it is not.")
    process(
      "is any boat stopped",
      "No, no boat is stopped.")
    process(
      "is any boat moving",
      "Yes, Lusitania is moving.")
    process(
      "is any vehicle stopped",
      "Yes, Herbie is stopped.")
    process(
      "is any vehicle moving",
      "Yes, Lusitania is moving.")
    process(
      "are both Herbie and Lusitania moving",
      "No, Herbie is not moving.")
    processMatrix(
      "is Lusitania sinking",
      "Yes, it is sinking.",
      "Yes, Lusitania is sinking.",
      "Yes.",
      "Yes, it is.")
    process(
      "is Herbie cruising",
      "I don't know.")
    processExceptionExpected(
      "is Herbie pink",
      "Sorry, I don't know what 'pink' means for Herbie.",
      ShlurdExceptionCode.UnknownState)
    processExceptionExpected(
      "is any car pink",
      "Sorry, I don't know what 'pink' means for a car.",
      ShlurdExceptionCode.UnknownState)
    processExceptionExpected(
      "who is pink",
      "Sorry, I don't know what 'pink' means for an spc-someone.",
      ShlurdExceptionCode.UnknownState)
    processMatrix(
      "is Herbie a car",
      "Yes, he is a car.",
      "Yes, Herbie is a car.",
      "Yes.",
      "Yes, he is.")
    processMatrix(
      "is Herbie a boat",
      "No, he is not a boat.",
      "No, Herbie is not a boat.",
      "No.",
      "No, he is not.")
    processMatrix(
      "is Herbie a vehicle",
      "Yes, he is a vehicle.",
      "Yes, Herbie is a vehicle.",
      "Yes.",
      "Yes, he is.")
    processMatrix(
      "is Lusitania a boat",
      "Yes, it is a boat.",
      "Yes, Lusitania is a boat.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is Lusitania the boat",
      "Yes, it is the boat.",
      "Yes, Lusitania is the boat.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is Lusitania a vehicle",
      "Yes, it is a vehicle.",
      "Yes, Lusitania is a vehicle.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is Lusitania a car",
      "No, it is not a car.",
      "No, Lusitania is not a car.",
      "No.",
      "No, it is not.")
    process(
      "how many vehicles are there",
      "There are two of them.")
    processMatrix(
      "Herbie and Lusitania are vehicles?",
      "Yes, they are vehicles.",
      "Yes, Herbie and Lusitania are vehicles.",
      "Yes.",
      "Yes, they are.")
    process(
      "which vehicles exist",
      "Herbie and Lusitania exist.")
    processMatrix(
      "who is Herbie's owner",
      "His owner is Jim.",
      "Herbie's owner is Jim.",
      "Jim.")
    processMatrix(
      "who is Lusitania's owner",
      "No one is its owner.",
      "No one is Lusitania's owner.",
      "No one.")
  }

  "deal with conjunctive plural noun" in new ResponderContext
  {
    loadBeliefs("/ontologies/person.txt")
    loadBeliefs("/ontologies/vehicles.txt")
    processMatrix(
      "are Herbie and Lusitania vehicles",
      "Yes, they are vehicles.",
      "Yes, Herbie and Lusitania are vehicles.",
      "Yes.",
      "Yes, they are.")
  }

  "respond correctly to disjunctive query" in new ResponderContext
  {
    loadBeliefs("/ontologies/person.txt")
    loadBeliefs("/ontologies/people.txt")
    processMatrix(
      "is Rapunzel or Amanda a dog",
      "Yes, one of them is a dog.",
      "Yes, one of them is a dog.",
      "Yes.",
      "Yes, one of them is.")
  }

  "respond correctly when no person exists" in new ResponderContext
  {
    processExceptionExpected(
      "who is Ford",
      "Sorry, I don't know about any 'Ford'.",
      ShlurdExceptionCode.UnknownForm)
  }

  "understand services" in new ResponderContext
  {
    loadBeliefs("/ontologies/service.txt")
    loadBeliefs("/ontologies/miscServices.txt")
    process(
      "is there a multimedia service",
      "Yes, there is a multimedia service.")
    process(
      "is there an alarm service",
      "Yes, there is an alarm service.")
    process(
      "is there a laundry service",
      "No, there is not a laundry service.")
    processMatrix(
      "is the alarm service up",
      "Yes, it is up.",
      "Yes, the alarm service is up.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is the alarm service on",
      "Yes, it is on.",
      "Yes, the alarm service is on.",
      "Yes.",
      "Yes, it is.")
    processMatrix(
      "is the multimedia service up",
      "No, it is not up.",
      "No, the multimedia service is not up.",
      "No.",
      "No, it is not.")
    process(
      "is any service up",
      "Yes, the alarm service is up.")
    process(
      "are any services up",
      "Yes, the alarm service is up.")
    process(
      "is any service down",
      "Yes, the multimedia service is down.")
    process(
      "is any service off",
      "Yes, the multimedia service is off.")
    process(
      "are all services up",
      "No, the multimedia service is not up.")
    processMatrix(
      "is the multimedia server up",
      "No, it is not up.",
      "No, the multimedia server is not up.",
      "No.",
      "No, it is not.")

    // FIXME progressive formation (should be running instead of runing!)
    process(
      "are all services running",
      "No, the multimedia service is not runing.")
  }

  "understand presence" in new ResponderContext
  {
    loadBeliefs("/ontologies/person.txt")
    loadBeliefs("/ontologies/presence.txt")
    processMatrix("is Jack's ubiety on",
      "Yes, his ubiety is on.",
      "Yes, Jack's ubiety is on.",
      "Yes.",
      "Yes, his ubiety is.")
    processMatrix("is Jack present",
      "Yes, he is present.",
      "Yes, Jack is present.",
      "Yes.",
      "Yes, he is.")
    processMatrix("is Jack at home",
      "Yes, he is at home.",
      "Yes, Jack is at home.",
      "Yes.",
      "Yes, he is.")
    processMatrix("is Jack home",
      "Yes, he is home.",
      "Yes, Jack is home.",
      "Yes.",
      "Yes, he is.")
    processMatrix("is Jack absent",
      "No, he is not absent.",
      "No, Jack is not absent.",
      "No.",
      "No, he is not.")
    processMatrix("is Jack away",
      "No, he is not away.",
      "No, Jack is not away.",
      "No.",
      "No, he is not.")
    processMatrix("is Jill's ubiety on",
      "No, her ubiety is not on.",
      "No, Jill's ubiety is not on.",
      "No.",
      "No, her ubiety is not.")
    processMatrix("is Jill present",
      "No, she is not present.",
      "No, Jill is not present.",
      "No.",
      "No, she is not.")
    processMatrix("is Jill at home",
      "No, she is not at home.",
      "No, Jill is not at home.",
      "No.",
      "No, she is not.")
    processMatrix("is Jill absent",
      "Yes, she is absent.",
      "Yes, Jill is absent.",
      "Yes.",
      "Yes, she is.")
    processMatrix("is Jill away",
      "Yes, she is away.",
      "Yes, Jill is away.",
      "Yes.",
      "Yes, she is.")
    processExceptionExpected(
      "is Jack on",
      "Sorry, I don't know what 'on' means for Jack.",
      ShlurdExceptionCode.UnknownState)
    processMatrix("is Casper's apparition on",
      "Yes, his apparition is on.",
      "Yes, Casper's apparition is on.",
      "Yes.",
      "Yes, his apparition is.")
    process("is Casper present",
      "I don't know.")
    processMatrix("is Yoda's ubiety on",
      "No, his ubiety is not on.",
      "No, Yoda's ubiety is not on.",
      "No.",
      "No, his ubiety is not.")
    processMatrix("is Yoda present",
      "No, he is not present.",
      "No, Yoda is not present.",
      "No.",
      "No, he is not.")
    processMatrix("is Yoda on",
      "No, he is not on.",
      "No, Yoda is not on.",
      "No.",
      "No, he is not.")
    processMatrix("is Yoda off",
      "Yes, he is off.",
      "Yes, Yoda is off.",
      "Yes.",
      "Yes, he is.")
    // FIXME should be "Yes, they are absent."
    processMatrix(
      "are Jill and Yoda absent",
      "Yes, Jill and Yoda are absent.",
      "Yes, Jill and Yoda are absent.",
      "Yes.",
      "Yes, Jill and Yoda are.")
  }

  "understand multiple properties for same form" in new ResponderContext
  {
    loadBeliefs("/ontologies/stove.txt")
    process("is there a stove?",
      "Yes, there is a stove.")
    process("is the stove hot?",
      "Yes, it is hot.")
    processMatrix("is the stove on?",
      "No, it is not on.",
      "No, the stove is not on.",
      "No.",
      "No, it is not.")
  }

  "understand equivalent queries" in new ResponderContext(
    ACCEPT_NEW_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("A vapor is a kind of object.")
    processBelief("A solid is a kind of object.")
    processBelief("Clint is a person.")
    processBelief("If a person sees a solid, " +
      "then equivalently the solid is in the person's container.")
    processBelief("Alcatraz is an object.")
    processBelief("The gold is a solid.")
    processBelief("The oxygen is a vapor.")
    processBelief("The gold is in Alcatraz.")
    processBelief("The oxygen is in Alcatraz.")
    processBelief("Clint is in Alcatraz.")
    processTerse(
      "what does Clint see",
      "The gold.")
  }

  "understand progressive action predicates" in new ResponderContext(
    ACCEPT_NEW_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("If an object is filling another object, " +
      "equivalently the former is the " +
      "latter's contained-object.")
    processBelief("If an object is occupying another object, " +
      "equivalently the former is in the latter.")
    processBelief("If an object is carrying another object, " +
      "then equivalently the former is the latter's container.")

    processBelief("The wallet is an object.")
    processBelief("The pocket is an object.")
    processBelief("The money is an object.")
    processBelief("The card is an object.")
    processBelief("The key is an object.")
    processBelief("The money is in the wallet.")
    processBelief("The card is in the wallet.")
    processBelief("The key is in the pocket.")
    process("how many objects are in the wallet",
      "Two of them are in it.")
    process("how many objects are in the pocket",
      "One of them is in it.")
    process("how many objects are the wallet's contained-object",
      "Two of them are its contained-objects.")
    process("how many objects are the pocket's contained-objects",
      "One of them is its contained-object.")
    processMatrix("how many objects are filling the wallet",
      "Two of them are filling it.",
      "Two of them are filling the wallet.",
      "Two of them.",
      "Two of them.")
    processMatrix("how many objects is the wallet carrying",
      "It is carrying two of them.",
      "The wallet is carrying two of them.",
      "Two of them.",
      "Two of them.")
    processMatrix("which objects is the wallet carrying",
      "It is carrying the money and the card.",
      "The wallet is carrying the money and the card.",
      "The money and the card.",
      "The money and the card.")
    processMatrix("what is the wallet carrying",
      "It is carrying the money and the card.",
      "The wallet is carrying the money and the card.",
      "The money and the card.",
      "The money and the card.")
    processMatrix("how many objects are occupying the pocket",
      "One of them is occupying it.",
      "One of them is occupying the pocket.",
      "One of them.",
      "One of them.")
    processMatrix("which objects are filling the wallet",
      "The money and the card are filling it.",
      "The money and the card are filling the wallet.",
      "The money and the card.",
      "The money and the card.")
    processMatrix("which objects are occupying the pocket",
      "The key is occupying it.",
      "The key is occupying the pocket.",
      "The key.",
      "The key.")
  }

  "understand state queries" in new ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    processBelief(
      "an animal's color must be white, gray, yellow, or green")
    processBelief("Leo is an animal.")
    processBelief("Leo is yellow.")
    processTerse("what color is Leo", "Yellow.")
  }

  "understand enum property queries" in new
    ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    processBelief("a sheep's color may be white or black")
    processBelief("Dolly is a sheep")
    process("what color is Dolly", "I don't know.")
    process("what is Dolly's color", "I don't know.")
    processBelief("Dolly is black")
    processMatrix(
      "what color is Dolly",
      "It is black.",
      "Dolly is black.",
      "Black.",
      "Black.")
    processMatrix(
      "what is Dolly's color",
      "Its color is black.",
      "Dolly's color is black.",
      "Black.",
      "Black.")
  }

  "understand string property queries" in new
    ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    val epigram = DQUOTE + "Lasciate ogni speranza, voi ch'entrate" + DQUOTE
    processBelief("a door's label must be an spc-string")
    processBelief("there is a door")
    processBelief("the door's label is " + epigram)
    processTerse("what is the door's label", epigram + ".")
    processTerse("is the door's label " + epigram, "Yes.")
  }

  "understand property updates" in new
    ResponderContext(ACCEPT_MODIFIED_BELIEFS)
  {
    processBelief("a door may be open or closed")
    processBelief("there is a door")
    processTerse("is the door open", "I don't know.")
    processBelief("the door is open")
    processTerse("is the door open", "Yes.")
    processTerse("is the door closed", "No.")
    processBelief("the door is closed")
    processTerse("is the door open", "No.")
    processTerse("is the door closed", "Yes.")

    cosmos.sanityCheck must beTrue
  }

  "understand compound nouns" in new
    ResponderContext(ACCEPT_NEW_BELIEFS)
  {
    processBelief("a butter knife is a kind of utensil")
    processBelief("there is a steak knife")
    process(
      "are there any butter knives",
      "No, there are no butter knives.")
    process(
      "are there any steak knives",
      "Yes, there is a steak knife.")
  }

  "handle missing objects" in new ResponderContext(
    ACCEPT_NEW_BELIEFS)
  {
    processBelief("a tomato is a kind of vegetable")
    processBelief("an apple is a kind of vegetable")
    processBelief("an apple is a kind of poison")
    processBelief("Pippin is an apple")
    processBelief("EarlyGirl is a tomato")
    processBelief("Kenrokuen is a garden")
    processBelief("Eden is a garden")
    processBelief("Filoli is a garden")
    processBelief("a garden's result must be a vegetable")
    processBelief("a garden may have results")
    processBelief("Pippin is Eden's result")
    processBelief("EarlyGirl is Filoli's result")
    processBelief("a person must be alive or dead")
    processBelief("Adam is a person")
    // processBelief("a person can eat")
    processBelief("if a person moves to a garden, " +
      "then the former eats the latter's results")
    processBelief("if a person eats a poison, " +
      "then the former is subsequently dead")
    processBelief("Adam is alive")
    processBelief("Adam moves to Kenrokuen")
    processTerse("is Adam dead", "No.")
    processBelief("Adam moves to Filoli")
    processTerse("is Adam dead", "No.")
    processBelief("Adam moves to Eden")
    processTerse("is Adam dead", "Yes.")
  }

  "disambiguate based on context" in new
    ResponderContext(ACCEPT_MODIFIED_BELIEFS)
  {
    loadBeliefs("/ontologies/containment.txt")
    processBelief("Daniel is a person")
    processBelief("a lion is a kind of object")
    processBelief("a lion may be sad or angry")
    processBelief("there is a big lion")
    processBelief("a stomach is a kind of object")
    processBelief("there is a stomach")
    processBelief("the lion is sad")
    processBelief("if a person kicks a lion, then the lion becomes angry")
    processTerse("Daniel kicks the lion in the stomach", "OK.")
    processTerse("is the lion angry", "Yes.")
    processBelief("there is a small lion")
    processBelief("the small lion is sad")
    processBelief("the big lion is sad")
    processExceptionExpected(
      "Daniel kicks the lion in the stomach",
      "Please be more specific about which lion you mean.",
      ShlurdExceptionCode.NotUnique)
    processBelief("the small lion is in the stomach")
    processTerse("which lion is in the stomach", "The small lion.")
    processTerse("Daniel kicks the lion in the stomach", "OK.")
    processTerse("is the small lion angry", "Yes.")
    processTerse("is the big lion angry", "No.")
  }

  "support roles with multiple forms" in new ResponderContext(
    ACCEPT_NEW_BELIEFS)
  {
    processBelief("a person is a kind of spc-someone")
    processBelief("a man is a kind of person")
    processBelief("a gentleman is a kind of man")
    processBelief("a gentleman's footman must be a man")
    processBelief("a gentleman's footman must be a plebeian")
    processBelief("a person's lord must be a gentleman")
    processBelief("if a gentleman is a man's lord, " +
      "then equivalently the man is the gentleman's footman")
    processBelief("Peter is a gentleman")
    processBelief("Bunter is Peter's footman")
    processTerse("is Bunter a footman", "Yes.")
    processTerse("is Bunter a man", "Yes.")
    processTerse("is Bunter a plebeian", "Yes.")
    processTerse("is Peter a gentleman", "Yes.")
    processTerse("who is Peter's footman", "Bunter.")
    processTerse("who is Bunter's lord", "Peter.")

    cosmos.sanityCheck must beTrue
  }

  "support role form collisions" in new ResponderContext(
    ACCEPT_NEW_BELIEFS)
  {
    processBelief("a squire is a kind of boy")
    processBelief("a knight may have a squire")
    processBelief("Quijote is a knight")
    processBelief("Sancho is Quijote's squire")
    processTerse("is Sancho a boy", "Yes.")
  }

  "understand compound names" in new ResponderContext(
    ACCEPT_NEW_BELIEFS)
  {
    processBelief("a professor is a kind of person")
    processBelief("a cop is a kind of person")
    processBelief("Harry Haller is a professor")
    processBelief("\"Dirty Harry\" must be a proper noun")
    processBelief("\"e e cummings\" must be a proper noun")
    processBelief("Dirty Harry is a cop")
    processBelief("e e cummings is a professor")
    processTerse("is Harry Haller a professor", "Yes.")
    processTerse("is cummings a professor", "Yes.")
    processTerse("is Cummings a professor", "Yes.")
    processTerse("is Harry Haller a cop", "No.")
    processTerse("which person is a professor",
      "Harry Haller and e e cummings.")
    processTerse("is Dirty Harry a professor", "No.")
    processTerse("is Dirty Harry a cop", "Yes.")
    processTerse("which person is a cop", "Dirty Harry.")
    processTerse("is Dirty a cop", "Yes.")
    processTerse("is Haller a cop", "No.")
    processTerse("is haller a cop", "No.")
    processExceptionExpected("is dirty a cop",
      "Sorry, I cannot understand what you said.",
      ShlurdExceptionCode.FailedParse)
    processExceptionExpected("is e a cop",
      "Sorry, I don't know about any 'e'.",
      ShlurdExceptionCode.UnknownForm)
    processExceptionExpected("is E a cop",
      "Sorry, I don't know about any 'E'.",
      ShlurdExceptionCode.UnknownForm)
    processTerse("is dirty harry a cop", "Yes.")
    processExceptionExpected(
      "is Harry a cop",
      "Please be more specific about which Harry you mean.",
      ShlurdExceptionCode.NotUnique)
  }

  "support transitive associations" in new ResponderContext(
    ACCEPT_NEW_BELIEFS)
  {
    processBelief("A patriarch's parent must be a patriarch.")
    processBelief("A patriarch's child must be a patriarch.")
    processBelief("A patriarch's ancestor must be a patriarch.")
    processBelief("A patriarch's descendant must be a patriarch.")
    processBelief("A patriarch may have a parent.")
    processBelief("A patriarch may have children.")
    processBelief("If a patriarch is another patriarch's child, " +
      "then equivalently the second patriarch is " +
      "the first patriarch's parent.")
    processBelief("A patriarch may have ancestors.")
    processBelief("A patriarch may have descendants.")
    processBelief("If a patriarch is another patriarch's descendant, " +
      "then equivalently the second patriarch is " +
      "the first patriarch's ancestor.")
    processBelief("If a patriarch begets another patriarch (the child), " +
      "then consequently the patriarch is the child's parent; " +
      "also the patriarch is the child's ancestor; " +
      "also the patriarch is the child's descendant's ancestor; " +
      "also the patriarch's descendant's ancestors " +
      "are the patriarch's ancestors.")
    processBelief("Abraham is a patriarch.")
    processBelief("Isaac is a patriarch.")
    processBelief("Jacob is a patriarch.")
    processBelief("Ishmael is a patriarch.")
    processBelief("Joseph is a patriarch.")
    processBelief("Abraham begets Isaac.")
    processBelief("Abraham begets Ishmael.")
    processBelief("Jacob begets Joseph.")
    processBelief("Isaac begets Jacob.")
    def answer(b : Boolean) = if (b) "Yes." else "No."
    Seq(
      ("Abraham", "Isaac", true, true),
      ("Abraham", "Jacob", false, true),
      ("Abraham", "Ishmael", true, true),
      ("Abraham", "Joseph", false, true),
      ("Isaac", "Jacob", true, true),
      ("Isaac", "Joseph", false, true),
      ("Jacob", "Joseph", true, true),
      ("Abraham", "Abraham", false, false),
      ("Isaac", "Ishmael", false, false),
      ("Ishmael", "Jacob", false, false),
      ("Ishmael", "Joseph", false, false)
    ).foreach {
      case (
        p1, p2, isParent, isAncestor
      ) => {
        processTerse(s"Is ${p1} ${p2}'s parent?", answer(isParent))
        processTerse(s"Is ${p1} ${p2}'s ancestor?", answer(isAncestor))
        if (isParent) {
          processTerse(s"Is ${p2} ${p1}'s child?", answer(true))
        }
        if (isAncestor) {
          processTerse(s"Is ${p2} ${p1}'s descendant?", answer(true))
        }
      }
    }
  }

  "understand custom compound nouns" in new ResponderContext(
    ACCEPT_MODIFIED_BELIEFS)
  {
    processBelief("\"big bad\" may be a noun")
    process("a big bad is a kind of villain", "OK.")
  }

  "understand custom pronouns" in new ResponderContext(
    ACCEPT_MODIFIED_BELIEFS)
  {
    loadBeliefs("/ontologies/person.txt")
    processBelief("\"ze\" is a nominative pronoun")
    processBelief("\"zir\" must be an objective pronoun or " +
      "a possessive pronoun")
    processBelief("\"zirself\" is a reflexive pronoun")
    processBelief("Pat is a person")
    processBelief("Chris is a person")
    processBelief("Alex is a person")
    processBelief("A person's fan must be a person")
    processBelief("Chris is Pat's fan")
    processBelief("Pat is Chris's fan")
    processBelief("If a person likes another person, " +
      "equivalently the former is the latter's fan")
    processBelief(
      "Pat's spc-pronoun-list is \"ze, zir, zirself\"")
    processBelief(
      "Alex's spc-pronoun-list is \"they, them, their, themself\"")

    mind.startConversation
    process("does Pat like zirself", "No, ze does not like zirself.")
    process("who is Pat", "Ze is Chris' fan.")
    process("who likes zir", "Chris likes zir.")
    process("who is zir fan", "Zir fan is Chris.")

    // Chris has no pronouns, so always refer to Chris by name
    process("who is Chris", "Chris is Pat's fan.")
    process("who likes Chris", "Pat likes Chris.")
    process("who is Chris' fan", "Chris' fan is Pat.")

    // Alex uses the singular they
    process("does Alex like themself", "No, they do not like themself.")
    process("who is Alex", "They are a person.")
    process("who likes them", "No one likes them.")
    process("who is their fan", "No one is their fan.")
  }
}
