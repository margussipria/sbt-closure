sbtPlugin := true

organization := "net.ground5hark.sbt"

name := "sbt-closure"

scalaVersion := "2.10.6"

libraryDependencies += "com.google.javascript" % "closure-compiler" % "v20160517"

addSbtPlugin("com.typesafe.sbt" %% "sbt-web" % "1.4.0")

scriptedSettings

scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  "-XX:MaxPermSize=512M",
  s"-Dproject.version=${version.value}"
)

