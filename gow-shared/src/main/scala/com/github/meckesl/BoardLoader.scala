package com.github.meckesl

import com.github.meckesl.repo.{RuleRepository, ScenarioRepository, TileRepository}
import com.github.meckesl.repo.TileRepository.{Tile, VoidTile}

object BoardLoader {

  def parse(text: String): Seq[Tile] =
    text.filter(TileRepository.all.map(_.char).contains(_)).map(TileRepository.getByChar)

  def parseUnits(unitsText: String): Seq[Tile] =
    parse(unitsText).map(u => if (TileRepository.units.contains(u)) u else VoidTile)

  def load(boardText: String, unitsText: String, width: Int): Unit = {
    RuleRepository.startingTerrain = parse(boardText).map(t => if (TileRepository.terrains.contains(t)) t else VoidTile)
    RuleRepository.startingUnits = parseUnits(unitsText)
    RuleRepository.squareX = width
    RuleRepository.squareY = RuleRepository.startingTerrain.size / width
  }

  /** Loads the terrain and every scenario; unitsByFile maps a scenario's units file to its text. */
  def loadScenarios(boardText: String, unitsByFile: Map[String, String], width: Int): Unit = {
    load(boardText, unitsByFile(ScenarioRepository.default.unitsFile), width)
    RuleRepository.scenarioUnits = ScenarioRepository.all.map(s => s.id -> parseUnits(unitsByFile(s.unitsFile))).toMap
  }
}
