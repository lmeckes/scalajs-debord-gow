package com.github.meckesl.protocol

import upickle.default.{macroRW, ReadWriter => RW}

sealed trait GameAction
object GameAction {
  case class Move(from: Int, to: Int) extends GameAction
  case class Attack(from: Int, target: Int) extends GameAction
  case class TakeArsenal(from: Int, to: Int) extends GameAction
  case object EndTurn extends GameAction

  implicit val moveRW: RW[Move] = macroRW
  implicit val attackRW: RW[Attack] = macroRW
  implicit val takeArsenalRW: RW[TakeArsenal] = macroRW
  implicit val endTurnRW: RW[EndTurn.type] = macroRW
  implicit val rw: RW[GameAction] = macroRW
}

sealed trait GameEvent
object GameEvent {
  case class Moved(from: Int, to: Int) extends GameEvent
  /** result: 0 repelled, 1 target must retreat, 2 destroyed, 3 removed because it could not retreat. */
  case class Attacked(from: Int, target: Int, result: Int) extends GameEvent
  case class ArsenalTaken(from: Int, to: Int) extends GameEvent
  case object TurnEnded extends GameEvent

  implicit val movedRW: RW[Moved] = macroRW
  implicit val attackedRW: RW[Attacked] = macroRW
  implicit val arsenalTakenRW: RW[ArsenalTaken] = macroRW
  implicit val turnEndedRW: RW[TurnEnded.type] = macroRW
  implicit val rw: RW[GameEvent] = macroRW
}

/** Full board snapshot: one char per square for units and facing ('E', 'W' or '.'). */
case class GameState(units: String, facing: String, turnPlayer: Char, remainingMoves: Int,
                     moved: Seq[Int], forcedRetreat: Option[Int])
object GameState {
  implicit val rw: RW[GameState] = macroRW
}

case class PlayerInfo(name: String, status: String)
object PlayerInfo {
  implicit val rw: RW[PlayerInfo] = macroRW
}

case class GameInfo(id: String, red: String, blue: String)
object GameInfo {
  implicit val rw: RW[GameInfo] = macroRW
}

sealed trait ClientMsg
object ClientMsg {
  case class Hello(name: String, token: Option[String]) extends ClientMsg
  case class Chat(text: String) extends ClientMsg
  case object QuickMatch extends ClientMsg
  case class CreatePrivate(scenario: String) extends ClientMsg
  case class JoinPrivate(code: String) extends ClientMsg
  case object CancelSearch extends ClientMsg
  case class Act(action: GameAction) extends ClientMsg
  case object Resign extends ClientMsg

  implicit val helloRW: RW[Hello] = macroRW
  implicit val chatRW: RW[Chat] = macroRW
  implicit val quickMatchRW: RW[QuickMatch.type] = macroRW
  implicit val createPrivateRW: RW[CreatePrivate] = macroRW
  implicit val joinPrivateRW: RW[JoinPrivate] = macroRW
  implicit val cancelSearchRW: RW[CancelSearch.type] = macroRW
  implicit val actRW: RW[Act] = macroRW
  implicit val resignRW: RW[Resign.type] = macroRW
  implicit val rw: RW[ClientMsg] = macroRW
}

sealed trait ServerMsg
object ServerMsg {
  case class Welcome(token: String, name: String) extends ServerMsg
  case class LobbyUpdate(players: Seq[PlayerInfo], games: Seq[GameInfo]) extends ServerMsg
  case class ChatMessage(from: String, text: String) extends ServerMsg
  case object Searching extends ServerMsg
  case class PrivateCreated(code: String) extends ServerMsg
  case object SearchCancelled extends ServerMsg
  case class MatchStarted(gameId: String, you: Char, red: String, blue: String, scenario: String, state: GameState) extends ServerMsg
  case class GameUpdate(state: GameState, event: GameEvent) extends ServerMsg
  case class OpponentConnection(connected: Boolean, forfeitSeconds: Int) extends ServerMsg
  case class GameOver(winner: Char, reason: String) extends ServerMsg
  case class ServerError(message: String) extends ServerMsg
  case object SessionReplaced extends ServerMsg

  implicit val welcomeRW: RW[Welcome] = macroRW
  implicit val lobbyUpdateRW: RW[LobbyUpdate] = macroRW
  implicit val chatMessageRW: RW[ChatMessage] = macroRW
  implicit val searchingRW: RW[Searching.type] = macroRW
  implicit val privateCreatedRW: RW[PrivateCreated] = macroRW
  implicit val searchCancelledRW: RW[SearchCancelled.type] = macroRW
  implicit val matchStartedRW: RW[MatchStarted] = macroRW
  implicit val gameUpdateRW: RW[GameUpdate] = macroRW
  implicit val opponentConnectionRW: RW[OpponentConnection] = macroRW
  implicit val gameOverRW: RW[GameOver] = macroRW
  implicit val serverErrorRW: RW[ServerError] = macroRW
  implicit val sessionReplacedRW: RW[SessionReplaced.type] = macroRW
  implicit val rw: RW[ServerMsg] = macroRW
}
