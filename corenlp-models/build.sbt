organization := "com.lingeringsocket.shlurd"

name := "shlurd-corenlp-models"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.8"

maxErrors := 99

traceLevel := 10

libraryDependencies ++= Seq(
  "edu.stanford.nlp" % "stanford-corenlp" % "3.9.1",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.9.1" classifier "models",
  "edu.stanford.nlp" % "stanford-corenlp" % "3.9.1" classifier "models-english"
)

publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath+"/.ivy2/local/com.lingeringsocket.shlurd")))

assemblyExcludedJars in assembly := { 
  val cp = (fullClasspath in assembly).value
  cp filter {!_.data.getName.startsWith("stanford-corenlp")}
}

def keepResource(s : String) : Boolean =
{
  val preservedResources = Set(
    "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger",
    "edu/stanford/nlp/models/parser/nndep/english_SD.gz",
    "edu/stanford/nlp/models/lexparser/englishRNN.ser.gz",
    "edu/stanford/nlp/models/srparser/englishSR.ser.gz",
    "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz")
  s.endsWith(".class") || s.endsWith(".properties") || preservedResources.contains(s)
}

assemblyMergeStrategy in assembly := {
  case s : String if (!keepResource(s)) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

artifact in (Compile, assembly) := {
  val art = (artifact in (Compile, assembly)).value
  art.copy(`classifier` = Some("assembly"))
}

addArtifact(artifact in (Compile, assembly), assembly)

test in assembly := {}