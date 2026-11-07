package com.github.meckesl.repo

import com.github.meckesl.repo.PlayerRepository.{Blue, Neutral, Player, Red}
import PlayerRepository._

object TileRepository {
  abstract case class Tile(char: Char, speed: Int, range: Int, attack: Int,
                           defense: Int, isCom: Boolean, player: Player)
  object VoidTile extends Tile('.', 0, 0, 0, 0, false, Neutral)
  object Fortress extends Tile('F', 0, 0, 0, 4, false, Neutral)
  object Mountain extends Tile('M', 0, 0, 0, 0, false, Neutral)
  object MountainPass extends Tile('=', 0, 0, 0, 2, false, Neutral)
  object RedArsenal extends Tile('A', 0, 0, 0, 0, true, Red)
  object RedRelay extends Tile('R', 1, 0, 0, 1, true, Red)
  object RedSwiftRelay extends Tile('E', 2, 0, 0, 1, true, Red)
  object RedCannon extends Tile('C', 1, 3, 5, 8, false, Red)
  object RedSwiftCannon extends Tile('N', 2, 3, 5, 8, false, Red)
  object RedInfantry extends Tile('I', 1, 2, 4, 6, false, Red)
  object RedCavalry extends Tile('V', 2, 2, 4, 5, false, Red)
  object BlueArsenal extends Tile('a', 0, 0, 0, 0, true, Blue)
  object BlueRelay extends Tile('r', 1, 0, 0, 1, true, Blue)
  object BlueSwiftRelay extends Tile('e', 2, 0, 0, 1, true, Blue)
  object BlueCannon extends Tile('c', 1, 3, 5, 8, false, Blue)
  object BlueSwiftCannon extends Tile('n', 2, 3, 5, 8, false, Blue)
  object BlueInfantry extends Tile('i', 1, 2, 4, 6, false, Blue)
  object BlueCavalry extends Tile('v', 2, 2, 4, 5, false, Blue)

  val cavalryChargeBonus = 3
  val cavalryChargeLength = 4

  val all: Set[Tile] = Set(VoidTile, Fortress, Mountain, MountainPass,
    RedArsenal, RedRelay, RedSwiftRelay, RedCannon, RedSwiftCannon, RedInfantry, RedCavalry,
    BlueArsenal, BlueRelay, BlueSwiftRelay, BlueCannon, BlueSwiftCannon, BlueInfantry, BlueCavalry)
  val terrains: Set[Tile] = all.filter(_.player.eq(Neutral))
  val units: Set[Tile] = all.filterNot(_.player.eq(Neutral))

  def getByChar(c: Char): Tile = all.filter(_.char.equals(c)).head

}