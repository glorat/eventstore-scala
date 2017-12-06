
name := "eventstore"

version := "0.2.0"

organization := "net.glorat"

description := "Scala implementation of a port of joliver's eventstore to Scala and Greg Young's .NET CQRS framework"

scalaVersion := "2.11.8"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


libraryDependencies ++= Seq(
"com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
"org.mongodb" %% "casbah-core" % "2.7.2",
"com.novus" %% "salat-core" % "1.9.8",
"joda-time" % "joda-time" % "2.2",
"org.scalatest" %% "scalatest" % "3.0.1" % "test",
//"junit" % "junit" % "4.11" % "test",
"ch.qos.logback" % "logback-classic" % "1.1.1",
"com.typesafe.akka" %% "akka-actor" % "2.4.17"
)

publishMavenStyle := true

pomIncludeRepository := { _ => false }

licenses := Seq("GNU LESSER GENERAL PUBLIC LICENSE" -> url("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt"))

homepage := Some(url("https://github.com/glorat/eventstore-scala"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/glorat/eventstore-scala"),
    "scm:git@github.com:glorat/eventstore-scala.git"
  )
)

developers := List(
  Developer(
    id    = "glorat",
    name  = "Kevin Tam",
    email = "kevin@glorat.net",
    url   = url("https://github.com/glorat")
  )
)
