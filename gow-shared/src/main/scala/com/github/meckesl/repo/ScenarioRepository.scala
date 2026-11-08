package com.github.meckesl.repo

/** Opening positions; all share init.board and differ only in unit placement. North is Red. */
object ScenarioRepository {
  case class Scenario(id: String, name: String, unitsFile: String)

  val default: Scenario = Scenario("default", "Default", "init.units")
  // Class Wargames battle maps (Richard Barbrook, "Class Wargames", designs by David Blanco).
  val all: Seq[Scenario] = Seq(
    default,
    Scenario("pumphouse", "Pump House", "pumphouse.units"),
    Scenario("rio", "Rio de Janeiro", "rio.units"),
    Scenario("marengo", "Marengo 1800", "marengo.units"),
    Scenario("austerlitz", "Austerlitz 1805", "austerlitz.units")
  )

  def byId(id: String): Option[Scenario] = all.find(_.id == id)
}
