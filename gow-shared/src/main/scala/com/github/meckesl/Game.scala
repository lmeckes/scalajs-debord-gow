package com.github.meckesl

import com.github.meckesl.repo.{CardinalityRepository, RuleRepository}
import com.github.meckesl.repo.CardinalityRepository.{Cardinality, SOURCE}
import com.github.meckesl.repo.PlayerRepository.{Blue, Neutral, Player, Red}
import com.github.meckesl.repo.TileRepository.{BlueArsenal, Mountain, RedArsenal, Tile, VoidTile}

import scala.collection.mutable
import scala.util.Random

class Game {

  var turnRemainingMoves: Int = RuleRepository.turnMoves
  val turnMovedUnits: mutable.Set[Square] = mutable.Set[Square]()
  var turnPlayer: Player = if (Random.nextBoolean()) Blue else Red

  var forcedRetreat: Option[Square] = None

  val capturedUnits: mutable.Seq[Tile] = mutable.Seq[Tile]()

  val gameSquares: Seq[Square] =
    0 until RuleRepository.squareCount map (i => {
      val sq = Square(i, RuleRepository.startingTerrain(i), this)
      sq.unit = RuleRepository.startingUnits(i)
      sq
    })

  def nextTurn(): Unit = {
    turnRemainingMoves = RuleRepository.turnMoves
    turnMovedUnits.clear()
    if (turnPlayer == Red)
      turnPlayer = Blue
    else
      turnPlayer = Red
  }

  /** A side loses when it has no arsenal left or no fighting (non-communication) unit left. */
  def winner: Option[Player] = {
    def defeated(pl: Player) = {
      val units = gameSquares.map(_.unit).filter(_.player == pl)
      !units.exists(u => u == RedArsenal || u == BlueArsenal) || !units.exists(!_.isCom)
    }
    (defeated(Red), defeated(Blue)) match {
      case (true, false) => Some(Blue)
      case (false, true) => Some(Red)
      case _ => None
    }
  }

  def refreshComLayer(): Unit = {

    def terrainBlocks(sq: Square) = sq.terrain.equals(Mountain)
    def opponentBlocks(sq: Square, pl: Player) = !Seq(Neutral, pl).contains(sq.unit.player)

    def processCommunication(source: Square, cursor: Square, dir: Set[Cardinality], isSubPropagationMode: Boolean): Unit = {
      val pl = source.unit.player

      if (pl.equals(Neutral) || (isSubPropagationMode && source.com(pl).isEmpty)) return

      val effectiveDir = if (isSubPropagationMode) dir.filterNot(_.equals(SOURCE)) else dir

      effectiveDir.foreach(d => {
        val sq = cursor.adjacentSquare(d)
        if (null != sq) {
          if (isSubPropagationMode) {
            if (sq.unit.player.equals(pl) && !sq.com(pl).contains(CardinalityRepository.opposite(d))) {
              cursor.com(pl) += d
            sq.com(pl) += CardinalityRepository.opposite(d)
            processCommunication(source, sq, Set(d), true)
            if (sq.unit.isCom)
              processCommunication(sq, sq, CardinalityRepository.all, false)
            }
          } else { // isPropagateMode
            if (!sq.com(pl).contains(CardinalityRepository.opposite(d))) {
              sq.com(pl) += CardinalityRepository.opposite(d)
              if (!terrainBlocks(sq) && (!opponentBlocks(sq, pl) || sq.unit.isCom)) {
                sq.com(pl) += d
                if (sq.unit.isCom && sq.unit.player.equals(pl))
                  processCommunication(sq, sq, CardinalityRepository.all, false)
                else
                  processCommunication(source, sq, Set(d), false)
              }
            }
          }
        }
      })
    }
    gameSquares.foreach(_.com.foreach(_._2.clear()))
    gameSquares.filter(sq => Seq(RedArsenal, BlueArsenal).contains(sq.unit))
      .foreach(sq => processCommunication(sq, sq, CardinalityRepository.all, false))
    gameSquares.filter(sq => !sq.unit.isCom && sq.com.values.nonEmpty && !sq.unit.player.equals(Neutral))
      .foreach(sq => processCommunication(sq, sq, CardinalityRepository.all, true))
  }

  refreshComLayer()

}
