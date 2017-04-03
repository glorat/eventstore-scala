
name := "eventstore"

version := "0.1.0"

organization := "net.glorat"

description := "Scala implementation of a port of joliver's eventstore to Scala and Greg Young's .NET CQRS framework"

scalaVersion := "2.11.8"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


libraryDependencies ++= Seq(
"com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
"org.mongodb" %% "casbah-core" % "2.7.2",
"com.novus" %% "salat-core" % "1.9.8",
"joda-time" % "joda-time" % "2.2",
"org.scalatest" %% "scalatest" % "2.2.4" % "test",
//"junit" % "junit" % "4.11" % "test",
"com.novocode" % "junit-interface" % "0.10" % "test",
"ch.qos.logback" % "logback-classic" % "1.1.1",
"com.typesafe.akka" %% "akka-actor" % "2.4.17"
)

// retrieveManaged := true

pomExtra := {
  <url>https://github.com/glorat/eventstore-scala</url>
    <licenses>
      <license>
        <name>GNU LESSER GENERAL PUBLIC LICENSE</name>
        <url>https://www.gnu.org/licenses/lgpl-3.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/glorat/eventstore-scala.git</connection>
      <developerConnection>scm:git:git@github.com/glorat/eventstore-scala.git</developerConnection>
      <url>github.com/glorat/eventstore-scala.git</url>
    </scm>
    <developers>
      <developer>
        <id>glorat</id>
        <name>Kevin Tam</name>
        <url>https://github.com/glorat/</url>
      </developer>
    </developers>
}
