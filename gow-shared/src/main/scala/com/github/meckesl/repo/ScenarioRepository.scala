package com.github.meckesl.repo

/** Opening positions: a board file for the terrain and a units file for the armies. North is Red. */
object ScenarioRepository {
  case class Scenario(id: String, name: String, boardFile: String, unitsFile: String, description: String)

  // Pump House to Austerlitz are Class Wargames battle maps (Richard Barbrook, "Class Wargames", designs by David Blanco).
  val all: Seq[Scenario] = Seq(
    Scenario("vanilla", "Vanilla", "init.board", "vanilla.units", "Default opening"),
    Scenario("pumphouse", "Pump House", "init.board", "pumphouse.units", "Class Wargames"),
    Scenario("rio", "Rio de Janeiro", "init.board", "rio.units", "Class Wargames"),
    Scenario("marengo", "Marengo 1800", "init.board", "marengo.units", "Class Wargames · Italy"),
    Scenario("austerlitz", "Austerlitz 1805", "init.board", "austerlitz.units", "Class Wargames · Moravia"),
    Scenario("escarmouche", "Escarmouche 8x12", "escarmouche.board", "escarmouche.units", "Small board · one fort")
  )

  def byId(id: String): Option[Scenario] = all.find(_.id == id)

  def files: Seq[String] = all.flatMap(s => Seq(s.boardFile, s.unitsFile)).distinct
}
