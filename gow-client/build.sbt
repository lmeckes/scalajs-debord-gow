enablePlugins(ScalaJSPlugin)

scalacOptions ++= Seq("-feature", "-deprecation", "-P:scalajs:nowarnGlobalExecutionContext")
scalaJSUseMainModuleInitializer := true
testFrameworks += new TestFramework("utest.runner.Framework")

organization := "com.github.meckesl"
name := "gow-client"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.13.16"
scalacOptions ++= Seq("-feature", "-deprecation")

resolvers += ("Typesafe" at "https://repo.typesafe.com/typesafe/releases/")
resolvers += ("Sonatype OSS" at "https://oss.sonatype.org/content/repositories/releases/")
libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "2.8.1",
  "com.lihaoyi" %%% "upickle" % "3.3.1",
  "com.lihaoyi" %%% "utest" % "0.9.0" % "test"
)