package com.github.meckesl.repo

import TileRepository.Tile

object RuleRepository {
  val turnMoves = 5
  val turnAttacks = 1
  val relaysEmitWhenChained = true
  var startingTerrain: Seq[Tile] = _
  var startingUnits: Seq[Tile] = _
  var squareX: Int = 0
  var squareY: Int = 0
  def squareCount: Int = squareX * squareY
}