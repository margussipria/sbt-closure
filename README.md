sbt-closure
===========
[![Build Status](https://api.travis-ci.org/margussipria/sbt-closure.png?branch=master)](https://travis-ci.org/margussipria/sbt-closure)

[sbt-web] plugin which integrates with [Google’s Closure Compiler].

Plugin
======
Add the plugin to your `project/plugins.sbt` (for sbt 0.13.5+ and 1.x):
```scala
addSbtPlugin("eu.sipria.sbt" % "sbt-closure" % "1.1.1")
```

Add the [Sonatype releases] resolver:
```scala
resolvers += Resolver.sonatypeRepo("releases")
```

Enable the [sbt-web] plugin for your project:
```scala
lazy val root = (project in file(".")).enablePlugins(SbtWeb)
```

Add the `closure` task to your asset pipeline in your `build.sbt`:
```scala
pipelineStages := Seq(closure)
```

License
=======
This code is licensed under the [MIT License].

[sbt-web]:https://github.com/sbt/sbt-web
[official documentation page]:https://developers.google.com/closure/compiler/docs/gettingstarted_app
[Google’s Closure Compiler]:https://developers.google.com/closure/compiler/
[MIT License]:http://opensource.org/licenses/MIT
[Sonatype releases]:https://oss.sonatype.org/content/repositories/releases/
