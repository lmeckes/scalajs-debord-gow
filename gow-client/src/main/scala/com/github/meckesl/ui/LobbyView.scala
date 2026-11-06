package com.github.meckesl.ui

import com.github.meckesl.{Game, GameEngine, GameSnapshot}
import com.github.meckesl.protocol.ClientMsg._
import com.github.meckesl.protocol.ServerMsg
import com.github.meckesl.protocol.ServerMsg._
import com.github.meckesl.ws.LobbyClient
import org.scalajs.dom
import org.scalajs.dom.{html, Event, KeyboardEvent}

import scala.scalajs.js.timers.setInterval

/** Lobby, matchmaking and in-game panel; drives the board from server messages. */
class LobbyView(ui: UiController, game: Game) {

  private def el[T](id: String): T = dom.document.getElementById(id).asInstanceOf[T]
  private def show(e: html.Element, visible: Boolean): Unit = e.style.display = if (visible) "block" else "none"

  private val connStatus = el[html.Div]("connStatus")
  private val loginView = el[html.Div]("loginView")
  private val nameInput = el[html.Input]("nameInput")
  private val lobbyView = el[html.Div]("lobbyView")
  private val meName = el[html.Element]("meName")
  private val quickBtn = el[html.Button]("quickBtn")
  private val privateBtn = el[html.Button]("privateBtn")
  private val cancelBtn = el[html.Button]("cancelBtn")
  private val codeInput = el[html.Input]("codeInput")
  private val searchStatus = el[html.Div]("searchStatus")
  private val shareLink = el[html.Input]("shareLink")
  private val playerList = el[html.UList]("playerList")
  private val gameList = el[html.UList]("gameList")
  private val gameView = el[html.Div]("gameView")
  private val gameTitle = el[html.Div]("gameTitle")
  private val gameStatus = el[html.Div]("gameStatus")
  private val opponentStatus = el[html.Div]("opponentStatus")
  private val resignBtn = el[html.Button]("resignBtn")
  private val leaveBtn = el[html.Button]("leaveBtn")
  private val chatLog = el[html.Div]("chatLog")
  private val chatInput = el[html.Input]("chatInput")

  // The token lives in sessionStorage so it survives a reload but each tab plays as its own guest.
  private val storage = dom.window.localStorage
  private val tabStorage = dom.window.sessionStorage
  private def stored(key: String): Option[String] = Option(storage.getItem(key)).filter(_.nonEmpty)
  private def token: Option[String] = Option(tabStorage.getItem("gow.token")).filter(_.nonEmpty)

  private var inGame = false
  private var local = false
  private var colour: Option[Char] = None
  private var names: Map[Char, String] = Map()
  private var result: Option[String] = None
  private var opponentDeadline: Option[Double] = None

  private val client: LobbyClient = new LobbyClient(
    onOpen = () => {
      connStatus.textContent = ""
      stored("gow.name") match {
        case Some(name) => client.send(Hello(name, token))
        case None => showLogin()
      }
    },
    onMessage = handle,
    onClosed = () => connStatus.textContent = "Connection lost, reconnecting..."
  )

  private def colourName(c: Char) = if (c == 'r') "Red" else "Blue"

  private def showLogin(): Unit = {
    show(loginView, visible = true)
    show(lobbyView, visible = false)
    nameInput.focus()
  }

  private def showLobby(): Unit = {
    show(loginView, visible = false)
    show(lobbyView, !inGame)
    show(gameView, inGame)
  }

  private def login(): Unit = {
    val name = nameInput.value.trim
    if (name.nonEmpty) {
      storage.setItem("gow.name", name)
      client.send(Hello(name, token))
    }
  }

  private def setSearching(text: String, link: Option[String] = None): Unit = {
    searchStatus.textContent = text
    show(cancelBtn, text.nonEmpty)
    show(shareLink, link.nonEmpty)
    shareLink.value = link.getOrElse("")
  }

  private def li(text: String): html.LI = {
    val item = dom.document.createElement("li").asInstanceOf[html.LI]
    item.textContent = text
    item
  }

  private def renderGame(): Unit = {
    val turn = game.turnPlayer.c
    gameTitle.textContent =
      if (local) "Local game"
      else colour.map(c => s"You play ${colourName(c)} against ${names.getOrElse(if (c == 'r') 'b' else 'r', "?")}").getOrElse("")
    gameStatus.textContent = result.getOrElse {
      val moves = s"${game.turnRemainingMoves} moves left, Space ends the turn"
      if (local) s"${colourName(turn)} to play: $moves"
      else if (colour.contains(turn)) s"Your turn: $moves"
      else s"${names.getOrElse(turn, "Opponent")} is playing..."
    }
    opponentStatus.textContent = opponentDeadline.filter(_ => result.isEmpty).map { deadline =>
      val secs = ((deadline - System.currentTimeMillis()) / 1000).toInt.max(0)
      s"Opponent disconnected, they forfeit in ${secs}s"
    }.getOrElse("")
    show(resignBtn, !local && result.isEmpty)
    show(leaveBtn, local || result.nonEmpty)
  }

  private def enterGame(isLocal: Boolean): Unit = {
    inGame = true
    local = isLocal
    result = None
    opponentDeadline = None
    setSearching("")
    showLobby()
  }

  private def finish(winner: Char, reason: String): Unit = {
    val who = if (local) colourName(winner) else if (colour.contains(winner)) "You" else names.getOrElse(winner, colourName(winner))
    result = Some(s"$who won by $reason")
    ui.controls = _ => false
    renderGame()
  }

  private def startLocal(): Unit = {
    val localGame = new Game
    enterGame(isLocal = true)
    colour = None
    ui.controls = _ => result.isEmpty
    ui.onAction = action =>
      GameEngine.applyAction(localGame, localGame.turnPlayer, action).foreach { event =>
        ui.showState(GameSnapshot.of(localGame), Some(event))
        localGame.winner.foreach(w => finish(w.c, "victory"))
      }
    ui.showState(GameSnapshot.of(localGame), None)
  }

  private def leaveGame(): Unit = {
    inGame = false
    ui.controls = _ => false
    showLobby()
  }

  private def appendChat(from: String, text: String): Unit = {
    val line = dom.document.createElement("div").asInstanceOf[html.Div]
    val author = dom.document.createElement("b")
    author.textContent = s"$from: "
    line.appendChild(author)
    line.appendChild(dom.document.createTextNode(text))
    chatLog.appendChild(line)
    chatLog.scrollTop = chatLog.scrollHeight.toDouble
  }

  private def joinCodeFromUrl(): Option[String] = {
    val search = dom.window.location.search.stripPrefix("?")
    search.split("&").map(_.split("=", 2)).collectFirst { case Array("join", code) if code.nonEmpty => code }
  }

  private def handle(msg: ServerMsg): Unit = msg match {
    case Welcome(token, name) =>
      tabStorage.setItem("gow.token", token)
      storage.setItem("gow.name", name)
      meName.textContent = name
      showLobby()
      joinCodeFromUrl().foreach { code =>
        dom.window.history.replaceState(null, "", dom.window.location.pathname)
        client.send(JoinPrivate(code))
      }

    case LobbyUpdate(players, games) =>
      playerList.innerHTML = ""
      players.foreach(p => playerList.appendChild(li(s"${p.name} (${p.status})")))
      gameList.innerHTML = ""
      if (games.isEmpty) gameList.appendChild(li("none"))
      games.foreach(g => gameList.appendChild(li(s"${g.red} (Red) vs ${g.blue} (Blue)")))

    case ChatMessage(from, text) => appendChat(from, text)

    case Searching => setSearching("Searching for an opponent...")

    case PrivateCreated(code) =>
      val location = dom.window.location
      setSearching(s"Private game $code: waiting for your friend. Share this link:",
        Some(s"${location.origin}${location.pathname}?join=$code"))

    case SearchCancelled => setSearching("")

    case MatchStarted(_, you, red, blue, state) =>
      enterGame(isLocal = false)
      colour = Some(you)
      names = Map('r' -> red, 'b' -> blue)
      ui.controls = p => result.isEmpty && colour.contains(p.c)
      ui.onAction = action => client.send(Act(action))
      ui.showState(state, None)

    case GameUpdate(state, event) => ui.showState(state, Some(event))

    case OpponentConnection(connected, seconds) =>
      opponentDeadline = if (connected) None else Some(System.currentTimeMillis().toDouble + seconds * 1000)
      renderGame()

    case GameOver(winner, reason) => if (!local) finish(winner, reason)

    case ServerError(message) =>
      if (inGame) gameStatus.textContent = message
      else setSearching(message)

    case SessionReplaced =>
      client.stop()
      connStatus.textContent = "This game is open in another window."
  }

  ui.onStateChange = () => renderGame()

  el[html.Button]("loginBtn").onclick = (_: Event) => login()
  nameInput.onkeydown = (e: KeyboardEvent) => if (e.key == "Enter") login()
  nameInput.value = stored("gow.name").getOrElse("")
  quickBtn.onclick = (_: Event) => client.send(QuickMatch)
  privateBtn.onclick = (_: Event) => client.send(CreatePrivate)
  cancelBtn.onclick = (_: Event) => client.send(CancelSearch)
  el[html.Button]("joinBtn").onclick = (_: Event) => if (codeInput.value.trim.nonEmpty) client.send(JoinPrivate(codeInput.value))
  el[html.Button]("localBtn").onclick = (_: Event) => startLocal()
  el[html.Button]("renameBtn").onclick = (_: Event) => showLogin()
  resignBtn.onclick = (_: Event) => if (dom.window.confirm("Resign this game?")) client.send(Resign)
  leaveBtn.onclick = (_: Event) => leaveGame()
  chatInput.onkeydown = (e: KeyboardEvent) =>
    if (e.key == "Enter" && chatInput.value.trim.nonEmpty) {
      client.send(Chat(chatInput.value))
      chatInput.value = ""
    }
  shareLink.onclick = (_: Event) => shareLink.select()
  setSearching("")
  show(gameView, visible = false)
  setInterval(1000)(if (opponentDeadline.nonEmpty && inGame) renderGame())
}
