sbtPlugin := true

name := "sbt-closure"

homepage := Some(new URL("https://github.com/margussipria/sbt-closure"))
licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

organization := "eu.sipria.sbt"

version := "1.1.0-SNAPSHOT"

scalaVersion := "2.12.4"

crossSbtVersions := Seq("0.13.17", "1.1.1")

libraryDependencies += "com.google.javascript" % "closure-compiler" % "v20180204"

addSbtPlugin("com.typesafe.sbt" %% "sbt-web" % "1.4.3")

scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  "-XX:MaxPermSize=512M",
  s"-Dproject.version=${version.value}"
)

developers := List(
  Developer("ground5hark", "John Bernard", "", url("https://github.com/ground5hark")),
  Developer("margussipria", "Margus Sipria", "margus+sbt-closure@sipria.fi", url("https://github.com/margussipria"))
)

scmInfo := Some(ScmInfo(
  url("https://github.com/margussipria/sbt-closure"),
  "scm:git:https://github.com/margussipria/sbt-closure.git",
  Some("scm:git:git@github.com:margussipria/sbt-closure.git")
))
