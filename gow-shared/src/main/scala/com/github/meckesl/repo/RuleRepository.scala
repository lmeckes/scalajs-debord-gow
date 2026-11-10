package com.github.meckesl.repo

import com.github.meckesl.Board
import TileRepository.Tile

object RuleRepository {
  val turnMoves = 5
  val turnAttacks = 1
  val relaysEmitWhenChained = true
  var scenarioBoards: Map[String, Board] = Map()
  var scenarioUnits: Map[String, Seq[Tile]] = Map()
}
