
name := "eventstore"

version := "0.4.0"

organization := "net.glorat"

description := "Scala implementation of a port of joliver's eventstore to Scala and Greg Young's .NET CQRS framework"

scalaVersion := "2.11.8"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += Resolver.bintrayRepo("cakesolutions", "maven")

libraryDependencies ++= Seq(
"com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
"com.novus" %% "salat-core" % "1.9.8",
"joda-time" % "joda-time" % "2.2",
"org.scalatest" %% "scalatest" % "3.0.1" % "test",
//"junit" % "junit" % "4.11" % "test",
"ch.qos.logback" % "logback-classic" % "1.1.1",
"com.typesafe.akka" %% "akka-actor" % "2.5.3",
  //"org.apache.kafka" % "kafka-clients" % "0.11.0.0",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.21" % "test",
  "net.cakesolutions" %% "scala-kafka-client" % "1.0.0",
  "net.cakesolutions" %% "scala-kafka-client-testkit" % "1.0.0" % "test"
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
