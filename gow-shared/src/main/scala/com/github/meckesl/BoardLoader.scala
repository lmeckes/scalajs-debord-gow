package com.github.meckesl

import com.github.meckesl.repo.{RuleRepository, ScenarioRepository, TileRepository}
import com.github.meckesl.repo.TileRepository.{Tile, VoidTile}

object BoardLoader {

  def parse(text: String): Seq[Tile] =
    text.filter(TileRepository.all.map(_.char).contains(_)).map(TileRepository.getByChar)

  def parseUnits(unitsText: String): Seq[Tile] =
    parse(unitsText).map(u => if (TileRepository.units.contains(u)) u else VoidTile)

  /** Rows are the lines holding tiles (the column header and row labels hold none); the width is the first row's. */
  def parseBoard(boardText: String): Board = {
    val rows = boardText.linesIterator.map(parse).filter(_.nonEmpty).toSeq
    require(rows.forall(_.size == rows.head.size), "board rows differ in length")
    Board(rows.head.size, rows.flatten.map(t => if (TileRepository.terrains.contains(t)) t else VoidTile))
  }

  /** Loads every scenario's board and units; `files` maps a file name to its text. */
  def loadScenarios(files: Map[String, String]): Unit = {
    val boards = ScenarioRepository.all.map(s => s.id -> parseBoard(files(s.boardFile))).toMap
    val units = ScenarioRepository.all.map(s => s.id -> parseUnits(files(s.unitsFile))).toMap
    ScenarioRepository.all.foreach(s =>
      require(units(s.id).size == boards(s.id).size, s"${s.unitsFile} does not match ${s.boardFile}"))
    RuleRepository.scenarioBoards = boards
    RuleRepository.scenarioUnits = units
  }
}
