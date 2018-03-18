libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.25"

addSbtPlugin("eu.sipria.sbt" %% "sbt-closure" % sys.props("project.version"))

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.12")

addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.3")
