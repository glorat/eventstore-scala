import sbt._
import Keys._

object EventstoreBuild extends Build {
  lazy val root = Project(id = "eventstore-root",
    base = file(".")) aggregate(eventstore, playstore)

//  lazy val eventstore = Project(id = "eventstore",
//    base = file("eventstore"))
  lazy val eventstore = project

//  lazy val playstore = Project(id = "playstore", base = file("playstore")).dependsOn(eventstore)
  lazy val playstore = project.dependsOn(eventstore % "compile->compile;compile->test").enablePlugins(play.PlayScala)

}
