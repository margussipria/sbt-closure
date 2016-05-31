sbtPlugin := true

organization := "net.ground5hark.sbt"

name := "sbt-closure"

scalaVersion := "2.10.6"

libraryDependencies += "com.google.javascript" % "closure-compiler" % "v20160517"

addSbtPlugin("com.typesafe.sbt" %% "sbt-web" % "1.4.0")

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.url("sbt snapshot plugins", url("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns),
  Resolver.sonatypeRepo("snapshots"),
  "Typesafe Snapshots Repository" at "http://repo.typesafe.com/typesafe/snapshots/",
  Resolver.mavenLocal
)

scriptedSettings

scriptedLaunchOpts ++= Seq(
  "-Xmx2048M",
  "-XX:MaxPermSize=512M",
  s"-Dproject.version=${version.value}"
)

