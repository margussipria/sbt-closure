libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.6.4"

addSbtPlugin("eu.sipria.sbt" %% "sbt-closure" % sys.props("project.version"))

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.1")
