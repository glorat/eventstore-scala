
name := "eventstore"

version := "0.1"

organization := "Glorat"

scalaVersion := "2.11.1"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


libraryDependencies ++= Seq(
"com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
"org.mongodb" %% "casbah-core" % "2.7.2",
"com.novus" %% "salat-core" % "1.9.8",
"joda-time" % "joda-time" % "2.2",
"org.scalatest" %% "scalatest" % "2.1.7" % "test",
//"junit" % "junit" % "4.11" % "test",
"com.novocode" % "junit-interface" % "0.10" % "test",
"ch.qos.logback" % "logback-classic" % "1.1.1",
"com.typesafe.akka" %% "akka-actor" % "2.3.3"
)

// retrieveManaged := true

