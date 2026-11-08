package com.github.meckesl.repo

/** Opening positions; all share init.board and differ only in unit placement. North is Red. */
object ScenarioRepository {
  case class Scenario(id: String, name: String, unitsFile: String, description: String)

  // All but Vanilla are Class Wargames battle maps (Richard Barbrook, "Class Wargames", designs by David Blanco).
  val all: Seq[Scenario] = Seq(
    Scenario("vanilla", "Vanilla", "vanilla.units", "Default opening"),
    Scenario("pumphouse", "Pump House", "pumphouse.units", "Class Wargames"),
    Scenario("rio", "Rio de Janeiro", "rio.units", "Class Wargames"),
    Scenario("marengo", "Marengo 1800", "marengo.units", "Class Wargames · Italy"),
    Scenario("austerlitz", "Austerlitz 1805", "austerlitz.units", "Class Wargames · Moravia")
  )

  def byId(id: String): Option[Scenario] = all.find(_.id == id)
}
