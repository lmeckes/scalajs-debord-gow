package com.github.meckesl

import com.github.meckesl.repo.TileRepository.Tile

/** A rectangular board's terrain, row by row. */
case class Board(width: Int, terrain: Seq[Tile]) {
  require(width > 0 && terrain.size % width == 0, s"${terrain.size} squares do not fill rows of $width")
  def height: Int = terrain.size / width
  def size: Int = terrain.size
}
