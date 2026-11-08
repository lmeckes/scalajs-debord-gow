import com.github.meckesl.{Game, GameSnapshot}
import com.github.meckesl.lobby.{Boards, Connection, Lobby}
import com.github.meckesl.protocol.{GameAction, GameEvent, GameState}
import com.github.meckesl.protocol.ClientMsg._
import com.github.meckesl.protocol.ServerMsg._
import com.github.meckesl.protocol.ServerMsg
import com.github.meckesl.repo.{RuleRepository, ScenarioRepository}
import com.github.meckesl.repo.TileRepository._
import utest._

import scala.collection.mutable

object LobbyTest extends TestSuite {

  Boards.loadDefault()

  class TestConn extends Connection {
    val received = mutable.ArrayBuffer[ServerMsg]()
    var closed = false
    def send(msg: ServerMsg): Unit = received += msg
    def close(): Unit = closed = true
    def last[T <: ServerMsg](implicit ct: scala.reflect.ClassTag[T]): T =
      received.reverseIterator.collectFirst { case m: T => m }.getOrElse(throw new java.lang.AssertionError(s"no ${ct.runtimeClass.getSimpleName} in $received"))
    def count[T <: ServerMsg](implicit ct: scala.reflect.ClassTag[T]): Int = received.count(ct.runtimeClass.isInstance)
  }

  class Fixture {
    var now = 0L
    val lobby = new Lobby(forfeitMillis = 120000L, clock = () => now)
    def join(name: String, token: Option[String] = None): TestConn = {
      val c = new TestConn
      lobby.handle(c, Hello(name, token))
      c
    }
    /** Two guests paired by quick match, returned as (player to move, waiting player). */
    def matched(): (TestConn, TestConn) = {
      val a = join("alice")
      val b = join("bob")
      lobby.handle(a, QuickMatch)
      lobby.handle(b, QuickMatch)
      val sa = a.last[MatchStarted]
      if (sa.you == sa.state.turnPlayer) (a, b) else (b, a)
    }
  }

  def legalMove(state: GameState): GameAction.Move = {
    val game = new Game
    GameSnapshot.applyTo(state, game)
    val moves = for {
      src <- game.gameSquares if src.unit.player == game.turnPlayer && src.canMove
      dst <- game.gameSquares if src.canMoveTo(dst)
    } yield GameAction.Move(src.index, dst.index)
    moves.head
  }

  val tests = Tests {
    test("quick match pairs the first two waiting guests") {
      val f = new Fixture
      val a = f.join("alice")
      val b = f.join("bob")
      val c = f.join("carol")
      f.lobby.handle(a, QuickMatch)
      assert(a.count[Searching.type] == 1)
      f.lobby.handle(b, QuickMatch)
      f.lobby.handle(c, QuickMatch)
      val sa = a.last[MatchStarted]
      val sb = b.last[MatchStarted]
      assert(sa.gameId == sb.gameId)
      assert(Set(sa.you, sb.you) == Set('r', 'b'))
      assert(c.count[MatchStarted] == 0)
      assert(c.count[Searching.type] == 1)
      val lobbyView = c.last[LobbyUpdate]
      assert(lobbyView.games.size == 1)
      assert(lobbyView.players.find(_.name == "carol").get.status == "searching")
    }

    test("private game is joined by code") {
      val f = new Fixture
      val a = f.join("alice")
      val b = f.join("bob")
      f.lobby.handle(a, CreatePrivate("default"))
      val code = a.last[PrivateCreated].code
      f.lobby.handle(b, QuickMatch)
      assert(a.count[MatchStarted] == 0)
      f.lobby.handle(a, JoinPrivate(code))
      assert(a.last[ServerError].message.contains("own game"))
      val c = f.join("carol")
      f.lobby.handle(c, JoinPrivate("nope"))
      assert(c.count[ServerError] == 1)
      f.lobby.handle(c, JoinPrivate(code.toLowerCase))
      assert(a.last[MatchStarted].gameId == c.last[MatchStarted].gameId)
    }

    test("private game starts on the host's scenario") {
      val f = new Fixture
      val a = f.join("alice")
      val b = f.join("bob")
      f.lobby.handle(a, CreatePrivate("nope"))
      assert(a.last[ServerError].message == "Unknown scenario")
      f.lobby.handle(a, CreatePrivate("marengo"))
      f.lobby.handle(b, JoinPrivate(a.last[PrivateCreated].code))
      val started = b.last[MatchStarted]
      assert(started.scenario == "marengo")
      assert(started.state.units == RuleRepository.scenarioUnits("marengo").map(_.char).mkString)
      assert(started.state.units != RuleRepository.startingUnits.map(_.char).mkString)
    }

    test("quick match uses the default scenario") {
      val f = new Fixture
      val (mover, _) = f.matched()
      val started = mover.last[MatchStarted]
      assert(started.scenario == ScenarioRepository.default.id)
      assert(started.state.units == RuleRepository.startingUnits.map(_.char).mkString)
    }

    test("every scenario deploys the standard armies") {
      val armies = Seq(RedInfantry -> 9, RedCavalry -> 4, RedCannon -> 1, RedSwiftCannon -> 1, RedRelay -> 1,
        RedSwiftRelay -> 1, RedArsenal -> 2, BlueInfantry -> 9, BlueCavalry -> 4, BlueCannon -> 1,
        BlueSwiftCannon -> 1, BlueRelay -> 1, BlueSwiftRelay -> 1, BlueArsenal -> 2)
      ScenarioRepository.all.foreach { s =>
        val units = RuleRepository.scenarioUnits(s.id)
        assert(units.size == RuleRepository.squareCount)
        armies.foreach { case (tile, n) => assert((s.id, tile, units.count(_ eq tile)) == ((s.id, tile, n))) }
        assert(units.zip(RuleRepository.startingTerrain).forall { case (u, t) => u == VoidTile || t != Mountain })
      }
    }

    test("out of turn actions are rejected") {
      val f = new Fixture
      val (mover, waiter) = f.matched()
      f.lobby.handle(waiter, Act(GameAction.EndTurn))
      assert(waiter.last[ServerError].message == "Not your turn")
      assert(mover.count[GameUpdate] == 0)
    }

    test("illegal move is rejected") {
      val f = new Fixture
      val (mover, _) = f.matched()
      f.lobby.handle(mover, Act(GameAction.Move(0, 0)))
      assert(mover.last[ServerError].message == "Illegal move")
      assert(mover.count[GameUpdate] == 0)
    }

    test("legal move is applied and broadcast") {
      val f = new Fixture
      val (mover, waiter) = f.matched()
      val start = mover.last[MatchStarted].state
      val move = legalMove(start)
      f.lobby.handle(mover, Act(move))
      val update = waiter.last[GameUpdate]
      assert(update.event == GameEvent.Moved(move.from, move.to))
      assert(update.state.remainingMoves == start.remainingMoves - 1)
      assert(update.state.units(move.to) == start.units(move.from))
      assert(mover.last[GameUpdate] == update)
    }

    test("ending the turn hands over to the opponent") {
      val f = new Fixture
      val (mover, waiter) = f.matched()
      val first = mover.last[MatchStarted].state.turnPlayer
      f.lobby.handle(mover, Act(GameAction.EndTurn))
      val state = waiter.last[GameUpdate].state
      assert(state.turnPlayer != first)
      assert(state.turnPlayer == waiter.last[MatchStarted].you)
      f.lobby.handle(waiter, Act(GameAction.EndTurn))
      assert(mover.last[GameUpdate].state.turnPlayer == first)
    }

    test("reconnecting with the token resumes the match") {
      val f = new Fixture
      val (mover, waiter) = f.matched()
      val token = mover.last[Welcome].token
      f.lobby.disconnected(mover)
      assert(!waiter.last[OpponentConnection].connected)
      f.now += 60000
      f.lobby.tick()
      val again = f.join("", Some(token))
      assert(again.last[Welcome].token == token)
      assert(again.last[MatchStarted].gameId == waiter.last[MatchStarted].gameId)
      assert(waiter.last[OpponentConnection].connected)
      f.now += 120000
      f.lobby.tick()
      assert(waiter.count[GameOver] == 0)
    }

    test("absent player forfeits after the grace period") {
      val f = new Fixture
      val (mover, waiter) = f.matched()
      val waiterColour = waiter.last[MatchStarted].you
      f.lobby.disconnected(mover)
      f.now += 119000
      f.lobby.tick()
      assert(waiter.count[GameOver] == 0)
      f.now += 1000
      f.lobby.tick()
      assert(waiter.last[GameOver] == GameOver(waiterColour, "forfeit"))
      f.lobby.handle(waiter, Act(GameAction.EndTurn))
      assert(waiter.last[ServerError].message == "You are not in a game")
    }

    test("resigning ends the match for both players") {
      val f = new Fixture
      val (mover, waiter) = f.matched()
      f.lobby.handle(waiter, Resign)
      val moverColour = mover.last[MatchStarted].you
      assert(mover.last[GameOver] == GameOver(moverColour, "resignation"))
      assert(waiter.last[GameOver] == GameOver(moverColour, "resignation"))
    }

    test("chat is broadcast to everyone") {
      val f = new Fixture
      val a = f.join("alice")
      val b = f.join("bob")
      f.lobby.handle(a, Chat("hello"))
      assert(b.last[ChatMessage] == ChatMessage("alice", "hello"))
      assert(a.last[ChatMessage] == ChatMessage("alice", "hello"))
    }

    test("actions require a nickname first") {
      val f = new Fixture
      val c = new TestConn
      f.lobby.handle(c, QuickMatch)
      assert(c.last[ServerError].message.contains("nickname"))
    }
  }
}
