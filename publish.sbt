publishTo in ThisBuild := {
  val isSnapshot = version.value.contains("-SNAPSHOT")
  val repo = "http://build.26source.org/nexus/content/repositories/"
  if (isSnapshot)
    Some("snapshots" at repo + "public-snapshots")
  else
    Some("releases"  at repo + "public-releases")
}

credentials in ThisBuild += Credentials(Path.userHome / ".ivy2" / ".credentials")
