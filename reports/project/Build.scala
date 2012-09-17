import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "reports"
  val appVersion = "1.0-SNAPSHOT"

  sourceDirectories in Compile += new File("../dal/src/")

  val postgresql = "postgresql" % "postgresql" % "9.1-901.jdbc4"
  val jodaTime = "joda-time" % "joda-time" % "2.1"
  val jodaTimeConvert = "org.joda" % "joda-convert" % "1.2"

  val appDependencies = Seq(
    postgresql,
    jodaTime,
    jodaTimeConvert
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    // Add your own project settings here
  )
}
