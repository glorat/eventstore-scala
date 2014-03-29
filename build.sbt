
name := "eventstore"

version := "0.1"

organization := "Glorat"

scalaVersion := "2.10.3"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


libraryDependencies ++= Seq(
"com.typesafe" %% "scalalogging-slf4j" % "1.0.1",
"org.mongodb" %% "casbah" % "2.6.0",
"com.novus" %% "salat" % "1.9.4",
"joda-time" % "joda-time" % "2.2",
"org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
//"junit" % "junit" % "4.11" % "test",
"com.novocode" % "junit-interface" % "0.10" % "test",
"ch.qos.logback" % "logback-classic" % "1.0.7"
)

// retrieveManaged := true

