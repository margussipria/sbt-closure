import com.google.javascript.jscomp.CompilerOptions
import com.typesafe.sbt.web.pipeline.Pipeline

organization := "eu.sipria.sbt"

name := "sbt-closure-test"

version := "0.1"

scalaVersion := "2.12.3"

lazy val root = (project in file(".")).enablePlugins(SbtWeb)

closureGroupFiles := Seq(
  "js/test1.js" -> ListOfFiles(Seq("js/hello.js", "js/assets-main.js")),
  "js/test2.js" -> ListOfFiles((((sourceDirectory in Assets).value / "js") * "*.js") --- (((sourceDirectory in Assets).value / "js") * "exclude.js"))
)

closureExcludeOriginal := true

closureExcludeGrouped := true

closureExtraOptions := {
  case "js/test1.js" | "js/test2.js" => options: CompilerOptions =>
    options.setDefineToBooleanLiteral("someFlag", true)
}

pipelineStages := Seq(closure)

val verifyFlags = taskKey[Unit]("Verify that all flags were provided to the compiler")

verifyFlags := {
  ((WebKeys.webTarget.value / closure.key.label) ** "test*.js").get.foreach { compiledFile =>
    val contents = IO.read(compiledFile)
    if (!contents.contains("someFlag=!0")) {
      sys.error(s"Expected 'someFlag=!0' but was: '$contents'")
    }
  }
}
