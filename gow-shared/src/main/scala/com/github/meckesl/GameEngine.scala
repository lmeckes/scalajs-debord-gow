package com.github.meckesl

import com.github.meckesl.protocol.{GameAction, GameEvent}
import com.github.meckesl.protocol.GameAction._
import com.github.meckesl.protocol.GameEvent._
import com.github.meckesl.repo.PlayerRepository.Player

/** Validates and applies a player's action; used by the server and by local hot-seat games. */
object GameEngine {

  def applyAction(game: Game, player: Player, action: GameAction): Either[String, GameEvent] = {
    def owned(i: Int) = game.gameSquares.lift(i).filter(_.unit.player == player)
    def square(i: Int) = game.gameSquares.lift(i)

    if (game.winner.isDefined) Left("The game is over")
    else if (player != game.turnPlayer) Left("Not your turn")
    else action match {
      case Move(from, to) =>
        (owned(from), square(to)) match {
          case (Some(src), Some(dst)) if src.canMoveTo(dst) =>
            src.moveUnitTo(dst)
            Right(Moved(from, to))
          case _ => Left("Illegal move")
        }
      case TakeArsenal(from, to) =>
        (owned(from), square(to)) match {
          case (Some(src), Some(dst)) if src.canTakeArsenal(dst) =>
            src.takeArsenal(dst)
            Right(ArsenalTaken(from, to))
          case _ => Left("Illegal arsenal capture")
        }
      case Attack(from, target) =>
        (owned(from), square(target)) match {
          case (Some(src), Some(dst)) if src.canAttack(dst) =>
            Right(Attacked(from, target, dst.launchAttackOn()))
          case _ => Left("Illegal attack")
        }
      case EndTurn =>
        game.nextTurn()
        Right(TurnEnded)
    }
  }
}
