organization := Common.organization

name := "shlurd"

version := Common.version

scalaVersion := Common.scalaVersion

scalastyleFailOnError := true

scalacOptions := Common.scalacOptions

maxErrors := Common.maxErrors

traceLevel := Common.traceLevel

lazy val rootProject = (project in file("."))

lazy val cli = project.dependsOn(rootProject)

lazy val root = rootProject.aggregate(cli)

libraryDependencies ++= Common.specs2Deps

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "com.googlecode.kiama" %% "kiama" % "1.8.0",
  "org.typelevel" %% "spire" % "0.14.1",
  "org.jgrapht" % "jgrapht-core" % "1.2.0",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.9.1",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.9.1" classifier "models",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.9.1" classifier "models-english"
)

publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath+"/.ivy2/local/com.lingeringsocket.shlurd")))

mainClass in Compile := Some("com.lingeringsocket.shlurd.cli.ShlurdCliApp")

fullClasspath in Runtime ++= (fullClasspath in cli in Runtime).value

scalacOptions in (Compile, console) := Common.scalacCommonOptions :+ "-Yrepl-sync"

initialCommands := """
import com.lingeringsocket.shlurd.parser._
import com.lingeringsocket.shlurd.print._
import com.lingeringsocket.shlurd.cosmos._
import com.lingeringsocket.shlurd.platonic._
"""
