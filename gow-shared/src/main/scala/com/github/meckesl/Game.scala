package com.github.meckesl

import com.github.meckesl.protocol.GameEvent.Moved
import com.github.meckesl.protocol.GameState
import com.github.meckesl.repo.{CardinalityRepository, RuleRepository}
import com.github.meckesl.repo.CardinalityRepository.{Cardinality, SOURCE}
import com.github.meckesl.repo.PlayerRepository.{Blue, Neutral, Player, Red}
import com.github.meckesl.repo.TileRepository.{BlueArsenal, Mountain, RedArsenal, Tile, VoidTile}

import scala.collection.mutable
import scala.util.Random

class Game(val board: Board, startingUnits: Seq[Tile]) {

  var turnRemainingMoves: Int = RuleRepository.turnMoves
  val turnMovedUnits: mutable.Set[Square] = mutable.Set[Square]()
  var turnPlayer: Player = if (Random.nextBoolean()) Blue else Red

  var forcedRetreat: Option[Square] = None

  /** This turn's latest moves, most recent first, each with the state it can be undone to. */
  var turnHistory: List[(GameState, Moved)] = Nil

  def recordMove(before: GameState, move: Moved): Unit =
    turnHistory = (before -> move :: turnHistory).take(RuleRepository.turnUndos)

  val capturedUnits: mutable.Seq[Tile] = mutable.Seq[Tile]()

  val gameSquares: Seq[Square] =
    0 until board.size map (i => {
      val sq = Square(i, board.terrain(i), this)
      sq.unit = startingUnits(i)
      sq
    })

  def nextTurn(): Unit = {
    turnRemainingMoves = RuleRepository.turnMoves
    turnMovedUnits.clear()
    turnHistory = Nil
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

  /** Lines run straight from arsenals (and relays standing on a line) until a mountain or an enemy unit;
    * units also connect through chains of adjacent friendly units leading to a unit on a line. */
  def refreshComLayer(): Unit = {
    gameSquares.foreach(_.com.foreach(_._2.clear()))
    Seq(Red, Blue).foreach(propagateCommunication)
  }

  private def propagateCommunication(pl: Player): Unit = {
    val directions = CardinalityRepository.all - SOURCE
    def blocks(sq: Square) = sq.terrain.equals(Mountain) || !Seq(Neutral, pl).contains(sq.unit.player)
    def friendly(sq: Square) = null != sq && sq.unit.player.equals(pl)

    val emitters = mutable.Queue[Square]()
    val emitting = mutable.Set[Square]()
    val chain = mutable.Queue[Square]()
    val linked = mutable.Set[Square]()
    def emit(sq: Square): Unit = if (emitting.add(sq)) emitters.enqueue(sq)
    def link(sq: Square): Unit = if (linked.add(sq)) {
      chain.enqueue(sq)
      if (sq.unit.isCom && RuleRepository.relaysEmitWhenChained) emit(sq)
    }

    gameSquares.filter(sq => friendly(sq) && Seq(RedArsenal, BlueArsenal).contains(sq.unit)).foreach(emit)

    while (emitters.nonEmpty || chain.nonEmpty) {
      while (emitters.nonEmpty) {
        val source = emitters.dequeue()
        source.com(pl) += SOURCE
        link(source)
        directions.foreach { d =>
          var from = source
          var next = source.adjacentSquare(d)
          while (null != next) {
            from.com(pl) += d
            next.com(pl) += CardinalityRepository.opposite(d)
            if (blocks(next)) next = null
            else {
              if (friendly(next)) {
                link(next)
                if (next.unit.isCom) emit(next)
              }
              from = next
              next = next.adjacentSquare(d)
            }
          }
        }
      }
      while (chain.nonEmpty) {
        val sq = chain.dequeue()
        directions.foreach { d =>
          val adj = sq.adjacentSquare(d)
          if (friendly(adj) && !linked.contains(adj)) {
            sq.com(pl) += d
            adj.com(pl) += CardinalityRepository.opposite(d)
            link(adj)
          }
        }
      }
    }
  }

  refreshComLayer()

}

object Game {
  def of(scenarioId: String): Game =
    new Game(RuleRepository.scenarioBoards(scenarioId), RuleRepository.scenarioUnits(scenarioId))
}
