organization := "com.github.meckesl"
name := "gow-server"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.13.16"
scalacOptions ++= Seq("-feature", "-deprecation")

resolvers += ("Typesafe" at "https://repo.typesafe.com/typesafe/releases/")
resolvers += ("Sonatype OSS" at "https://oss.sonatype.org/content/repositories/releases/")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.5.3",
  "com.typesafe.akka" %% "akka-stream" % "2.8.6",
  "com.lihaoyi" %% "utest" % "0.9.0" % "test"
)
testFrameworks += new TestFramework("utest.runner.Framework")
run / fork := true
Test / fork := true
