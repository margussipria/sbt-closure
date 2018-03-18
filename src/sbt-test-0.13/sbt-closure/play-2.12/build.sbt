lazy val root = (project in file("."))
  .enablePlugins(PlayScala, SbtWeb)
  .settings(
    name := "sbt-closure-test",

    organization := "eu.sipria.sbt",

    version := "0.1",

    scalaVersion := "2.12.4",

    pipelineStages := Seq(closure, digest)
  )
