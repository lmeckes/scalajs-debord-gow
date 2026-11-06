package com.github.meckesl

import com.github.meckesl.repo.{RuleRepository, TileRepository}
import com.github.meckesl.repo.TileRepository.{Tile, VoidTile}

object BoardLoader {

  def parse(text: String): Seq[Tile] =
    text.filter(TileRepository.all.map(_.char).contains(_)).map(TileRepository.getByChar)

  def load(boardText: String, unitsText: String, width: Int): Unit = {
    RuleRepository.startingTerrain = parse(boardText).map(t => if (TileRepository.terrains.contains(t)) t else VoidTile)
    RuleRepository.startingUnits = parse(unitsText).map(u => if (TileRepository.units.contains(u)) u else VoidTile)
    RuleRepository.squareX = width
    RuleRepository.squareY = RuleRepository.startingTerrain.size / width
  }
}
