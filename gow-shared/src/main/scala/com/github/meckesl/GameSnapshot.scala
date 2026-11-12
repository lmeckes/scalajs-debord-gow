package com.github.meckesl

import com.github.meckesl.protocol.GameState
import com.github.meckesl.repo.{CardinalityRepository, PlayerRepository, TileRepository}

object GameSnapshot {

  def of(game: Game): GameState = GameState(
    units = game.gameSquares.map(_.unit.char).mkString,
    facing = game.gameSquares.map(_.lastMoveDir match {
      case CardinalityRepository.E => 'E'
      case CardinalityRepository.W => 'W'
      case _ => '.'
    }).mkString,
    turnPlayer = game.turnPlayer.c,
    remainingMoves = game.turnRemainingMoves,
    moved = game.turnMovedUnits.map(_.index).toSeq.sorted,
    forcedRetreat = game.forcedRetreat.map(_.index),
    undoable = game.turnHistory.size
  )

  def applyTo(state: GameState, game: Game): Unit = {
    game.gameSquares.foreach { sq =>
      sq.unit = TileRepository.getByChar(state.units(sq.index))
      sq.lastMoveDir = state.facing(sq.index) match {
        case 'E' => CardinalityRepository.E
        case 'W' => CardinalityRepository.W
        case _ => CardinalityRepository.SOURCE
      }
    }
    game.turnPlayer = PlayerRepository.byChar(state.turnPlayer)
    game.turnRemainingMoves = state.remainingMoves
    game.turnMovedUnits.clear()
    state.moved.foreach(i => game.turnMovedUnits += game.gameSquares(i))
    game.forcedRetreat = state.forcedRetreat.map(game.gameSquares)
    game.refreshComLayer()
  }
}
