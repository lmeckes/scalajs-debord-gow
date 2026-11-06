package com.github.meckesl.lobby

import com.github.meckesl.{Game, GameEngine, GameSnapshot}
import com.github.meckesl.protocol._
import com.github.meckesl.protocol.ClientMsg._
import com.github.meckesl.protocol.ServerMsg._
import com.github.meckesl.repo.PlayerRepository.{Blue, Player, Red}

import java.util.UUID
import scala.collection.mutable
import scala.util.Random

trait Connection {
  def send(msg: ServerMsg): Unit
  def close(): Unit
}

/** Guests, matchmaking and running matches. All state changes go through synchronized entry points. */
class Lobby(forfeitMillis: Long = 120000L, clock: () => Long = () => System.currentTimeMillis()) {

  private class Guest(val token: String) {
    var name: String = ""
    var conn: Option[Connection] = None
    var disconnectedAt: Option[Long] = None
    var game: Option[Match] = None
    def send(msg: ServerMsg): Unit = conn.foreach(_.send(msg))
  }

  private class Match(val id: String, val red: Guest, val blue: Guest) {
    val game = new Game
    def guests: Seq[Guest] = Seq(red, blue)
    def colourOf(g: Guest): Player = if (g eq red) Red else Blue
    def opponentOf(g: Guest): Guest = if (g eq red) blue else red
    def started(g: Guest): MatchStarted = MatchStarted(id, colourOf(g).c, red.name, blue.name, GameSnapshot.of(game))
  }

  private val guests = mutable.Map[String, Guest]()
  private val connections = mutable.Map[Connection, Guest]()
  private val quickQueue = mutable.ListBuffer[Guest]()
  private val privateGames = mutable.Map[String, Guest]()
  private val matches = mutable.LinkedHashMap[String, Match]()

  def handle(conn: Connection, msg: ClientMsg): Unit = synchronized {
    msg match {
      case Hello(name, token) => hello(conn, name, token)
      case other => connections.get(conn) match {
        case Some(g) => handleGuest(g, other)
        case None => conn.send(ServerError("Choose a nickname first"))
      }
    }
  }

  def disconnected(conn: Connection): Unit = synchronized {
    connections.remove(conn).foreach { g =>
      g.conn = None
      g.disconnectedAt = Some(clock())
      cancelSearch(g)
      g.game.foreach(_.opponentOf(g).send(OpponentConnection(connected = false, (forfeitMillis / 1000).toInt)))
      broadcastLobby()
    }
  }

  /** Forfeits matches of guests who stayed away too long and forgets them. */
  def tick(): Unit = synchronized {
    val now = clock()
    guests.values.toList.foreach { g =>
      if (g.disconnectedAt.exists(now - _ >= forfeitMillis)) {
        g.game.foreach(m => endMatch(m, m.colourOf(m.opponentOf(g)), "forfeit"))
        guests -= g.token
      }
    }
  }

  private def hello(conn: Connection, name: String, token: Option[String]): Unit = {
    if (connections.contains(conn)) {
      conn.send(ServerError("Already identified"))
      return
    }
    val g = token.flatMap(guests.get).getOrElse {
      val ng = new Guest(UUID.randomUUID().toString)
      guests(ng.token) = ng
      ng
    }
    g.conn.foreach { old =>
      connections -= old
      old.send(SessionReplaced)
      old.close()
    }
    val clean = name.trim.filterNot(_.isControl).take(20)
    g.name = if (clean.nonEmpty) clean else if (g.name.nonEmpty) g.name else s"Guest-${g.token.take(4)}"
    g.conn = Some(conn)
    g.disconnectedAt = None
    connections(conn) = g
    g.send(Welcome(g.token, g.name))
    g.game.foreach { m =>
      g.send(m.started(g))
      val opp = m.opponentOf(g)
      opp.disconnectedAt.foreach { since =>
        g.send(OpponentConnection(connected = false, ((forfeitMillis - (clock() - since)) / 1000).toInt.max(0)))
      }
      opp.send(OpponentConnection(connected = true, 0))
    }
    broadcastLobby()
  }

  private def handleGuest(g: Guest, msg: ClientMsg): Unit = msg match {
    case Chat(text) =>
      val t = text.trim.filterNot(_.isControl).take(300)
      if (t.nonEmpty) connected.foreach(_.send(ChatMessage(g.name, t)))

    case QuickMatch =>
      if (busy(g)) g.send(ServerError("You are already searching or playing"))
      else quickQueue.headOption match {
        case Some(opp) =>
          quickQueue -= opp
          startMatch(opp, g)
        case None =>
          quickQueue += g
          g.send(Searching)
          broadcastLobby()
      }

    case CreatePrivate =>
      if (busy(g)) g.send(ServerError("You are already searching or playing"))
      else {
        val code = newCode()
        privateGames(code) = g
        g.send(PrivateCreated(code))
        broadcastLobby()
      }

    case JoinPrivate(rawCode) =>
      val code = rawCode.trim.toUpperCase
      privateGames.get(code) match {
        case None => g.send(ServerError("No open game with this code"))
        case Some(host) if host eq g => g.send(ServerError("You cannot join your own game"))
        case Some(_) if g.game.nonEmpty => g.send(ServerError("You are already playing"))
        case Some(host) =>
          cancelSearch(g)
          privateGames -= code
          startMatch(host, g)
      }

    case CancelSearch =>
      cancelSearch(g)
      g.send(SearchCancelled)
      broadcastLobby()

    case Act(action) =>
      g.game match {
        case None => g.send(ServerError("You are not in a game"))
        case Some(m) =>
          GameEngine.applyAction(m.game, m.colourOf(g), action) match {
            case Left(err) => g.send(ServerError(err))
            case Right(event) =>
              val update = GameUpdate(GameSnapshot.of(m.game), event)
              m.guests.foreach(_.send(update))
              m.game.winner.foreach(w => endMatch(m, w, "victory"))
          }
      }

    case Resign =>
      g.game.foreach(m => endMatch(m, m.colourOf(m.opponentOf(g)), "resignation"))

    case Hello(_, _) => ()
  }

  private def busy(g: Guest): Boolean =
    g.game.nonEmpty || quickQueue.contains(g) || privateGames.values.exists(_ eq g)

  private def cancelSearch(g: Guest): Unit = {
    quickQueue -= g
    privateGames.filterInPlace((_, host) => host ne g)
  }

  private def startMatch(a: Guest, b: Guest): Unit = {
    val (red, blue) = if (Random.nextBoolean()) (a, b) else (b, a)
    val m = new Match(UUID.randomUUID().toString.take(8), red, blue)
    matches(m.id) = m
    m.guests.foreach { g =>
      g.game = Some(m)
      g.send(m.started(g))
    }
    broadcastLobby()
  }

  private def endMatch(m: Match, winner: Player, reason: String): Unit = {
    matches -= m.id
    m.guests.foreach { g =>
      g.game = None
      g.send(GameOver(winner.c, reason))
    }
    broadcastLobby()
  }

  private def newCode(): String = {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    Iterator.continually(Seq.fill(5)(alphabet(Random.nextInt(alphabet.length))).mkString)
      .find(c => !privateGames.contains(c)).get
  }

  private def connected: Seq[Guest] = guests.values.filter(_.conn.nonEmpty).toSeq.sortBy(_.name.toLowerCase)

  private def status(g: Guest): String =
    if (g.game.nonEmpty) "playing"
    else if (quickQueue.contains(g) || privateGames.values.exists(_ eq g)) "searching"
    else "idle"

  private def broadcastLobby(): Unit = {
    val update = LobbyUpdate(
      connected.map(g => PlayerInfo(g.name, status(g))),
      matches.values.map(m => GameInfo(m.id, m.red.name, m.blue.name)).toSeq)
    connected.foreach(_.send(update))
  }
}
