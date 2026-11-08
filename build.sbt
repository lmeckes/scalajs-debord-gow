import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

ThisBuild / scalaVersion := "2.13.16"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("gow-shared"))
  .settings(
    organization := "com.github.meckesl",
    name := "gow-shared",
    version := "0.1.0-SNAPSHOT",
    scalacOptions ++= Seq("-feature", "-deprecation"),
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "3.3.1"
  )

lazy val sharedJS = shared.js
lazy val sharedJVM = shared.jvm

lazy val client = project.in(file("gow-client"))
  .dependsOn(sharedJS)
  .settings(
    // Board and scenario files are compiled into the JS so that index.html also works from file://
    Compile / sourceGenerators += Def.task {
      val files = ((Compile / resourceDirectory).value * ("*.board" || "*.units")).get().sortBy(_.getName)
      def literal(text: String) = text.flatMap {
        case '"' => "\\\""
        case '\\' => "\\\\"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c if c < ' ' || c > '~' => "\\u%04x".format(c.toInt)
        case c => c.toString
      }
      val entries = files.map(f => "    \"" + f.getName + "\" -> \"" + literal(IO.read(f)) + "\"").mkString(",\n")
      val out = (Compile / sourceManaged).value / "com" / "github" / "meckesl" / "io" / "EmbeddedResources.scala"
      IO.write(out, "package com.github.meckesl.io\n\nobject EmbeddedResources {\n  val files: Map[String, String] = Map(\n" + entries + "\n  )\n}\n")
      Seq(out)
    }.taskValue
  )

lazy val server = project.in(file("gow-server"))
  .dependsOn(sharedJVM)
  .settings(
    inConfig(Compile)(Seq(
      resourceGenerators += Def.task {
        // Client resources
        val indexFile = (client / baseDirectory).value / "index.html"
        val staticFiles = (client / resourceDirectory).value ** "*"
        val jsBinary = (client / fullOptJS).value.data
        val jsSourceMap = jsBinary.getParentFile / (jsBinary.getName + ".map")
        // Replicate folder structure
        val webFolder = resourceManaged.value / "web"
        val outputFolder = webFolder / "target" / "scala-2.13"
        IO.copy(Seq(
          (jsBinary, outputFolder / jsBinary.getName),
          (jsSourceMap, outputFolder / jsSourceMap.getName),
          (indexFile, webFolder / indexFile.getName))
        )
        val staticResources = staticFiles.get()
          .pair(Path.rebase((client / resourceDirectory).value, outputFolder / "classes")).map {
            case (in, out) => IO.copyDirectory(in, out)
              out
          }
        // Outputs resources items
        Seq(
          webFolder / indexFile.getName,
          outputFolder / jsBinary.getName,
          outputFolder / jsSourceMap.getName
        ) ++ staticResources
      }
    ))
  )

