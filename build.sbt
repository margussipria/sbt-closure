sbtPlugin := true

name := "sbt-closure"

organization := "eu.sipria.sbt"

version := "1.0.0-SNAPSHOT"

scalaVersion := "2.10.6"

libraryDependencies += "com.google.javascript" % "closure-compiler" % "v20170806"

addSbtPlugin("com.typesafe.sbt" %% "sbt-web" % "1.4.1")

scriptedSettings

scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  "-XX:MaxPermSize=512M",
  s"-Dproject.version=${version.value}"
)
