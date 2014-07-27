import sbt._
import Keys._
import play.PlayScala

object PlaystoreBuild extends Build {
  lazy val my = Project(
	id = "playstore",
	base = file(".")
	).enablePlugins(PlayScala)
}
