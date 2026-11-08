package com.github.meckesl.ui

import com.github.meckesl.{Game, GameEngine, GameSnapshot}
import com.github.meckesl.repo.ScenarioRepository.Scenario
import com.github.meckesl.protocol.ClientMsg._
import com.github.meckesl.protocol.{GameEvent, ServerMsg}
import com.github.meckesl.repo.{RuleRepository, ScenarioRepository}
import com.github.meckesl.protocol.ServerMsg._
import com.github.meckesl.ws.LobbyClient
import org.scalajs.dom
import org.scalajs.dom.{html, Event, KeyboardEvent}

import scala.scalajs.js.timers.{clearTimeout, setInterval, setTimeout, SetTimeoutHandle}

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
  private val endTurnBtn = el[html.Button]("endTurnBtn")
  private val meAvatar = el[html.Element]("meAvatar")
  private val turnDot = el[html.Element]("turnDot")
  private val movePips = el[html.Div]("movePips")
  private val movesLabel = el[html.Div]("movesLabel")
  private val toast = el[html.Div]("toast")
  private var toastTimer: Option[SetTimeoutHandle] = None
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
  private var scenarioName = ""
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
    dom.document.body.classList.toggle("in-game", inGame)
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

  private def li(text: String, cls: String = ""): html.LI = {
    val item = dom.document.createElement("li").asInstanceOf[html.LI]
    item.textContent = text
    item.className = cls
    item
  }

  private def span(text: String, cls: String): html.Span = {
    val s = dom.document.createElement("span").asInstanceOf[html.Span]
    s.textContent = text
    s.className = cls
    s
  }

  private def playerItem(name: String, status: String): html.LI = {
    val item = li("")
    item.appendChild(span("", if (status == "idle") "status-dot" else "status-dot busy"))
    item.appendChild(span(name, ""))
    item.appendChild(span(status, "pill-status"))
    item
  }

  private def gameItem(red: String, blue: String): html.LI = {
    val item = li("")
    item.appendChild(span(red, "team-name red"))
    item.appendChild(span("vs", "vs-small"))
    item.appendChild(span(blue, "team-name blue"))
    item
  }

  private def showToast(text: String): Unit = {
    toast.textContent = text
    toast.classList.add("show")
    toastTimer.foreach(clearTimeout)
    toastTimer = Some(setTimeout(2200)(toast.classList.remove("show")))
  }

  private def renderGame(): Unit = {
    val turn = game.turnPlayer.c
    val title =
      if (local) "Local game"
      else colour.map(c => s"You play ${colourName(c)} against ${names.getOrElse(if (c == 'r') 'b' else 'r', "?")}").getOrElse("")
    gameTitle.textContent = if (scenarioName.nonEmpty) s"$title · $scenarioName" else title
    gameStatus.textContent = result.getOrElse {
      if (local) s"${colourName(turn)} to play"
      else if (colour.contains(turn)) "Your turn"
      else s"${names.getOrElse(turn, "Opponent")} is playing..."
    }
    turnDot.className = if (result.isEmpty) turn.toString else ""
    movePips.className = turn.toString
    movePips.innerHTML = ""
    if (result.isEmpty)
      (0 until RuleRepository.turnMoves).foreach { i =>
        movePips.appendChild(span("", if (i < game.turnRemainingMoves) "on" else ""))
      }
    movesLabel.textContent =
      if (result.nonEmpty) ""
      else if (game.forcedRetreat.isDefined) "Retreat the attacked unit first"
      else s"${game.turnRemainingMoves} of ${RuleRepository.turnMoves} moves left"
    if (result.isEmpty && inGame) dom.document.body.setAttribute("data-turn", turn.toString)
    else dom.document.body.removeAttribute("data-turn")
    show(endTurnBtn, result.isEmpty && ui.canAct && game.forcedRetreat.isEmpty)
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
    val scenario = scenarioPicker.value
    val localGame = new Game(RuleRepository.scenarioUnits(scenario.id))
    enterGame(isLocal = true)
    scenarioName = scenario.name
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
    dom.document.body.removeAttribute("data-turn")
    showLobby()
    preview(scenarioPicker.value)
  }

  private def preview(scenario: Scenario): Unit =
    if (!inGame) ui.showState(GameSnapshot.of(new Game(RuleRepository.scenarioUnits(scenario.id))), None)

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
      meAvatar.textContent = name.take(1).toUpperCase
      showLobby()
      joinCodeFromUrl().foreach { code =>
        dom.window.history.replaceState(null, "", dom.window.location.pathname)
        client.send(JoinPrivate(code))
      }

    case LobbyUpdate(players, games) =>
      playerList.innerHTML = ""
      players.foreach(p => playerList.appendChild(playerItem(p.name, p.status)))
      gameList.innerHTML = ""
      if (games.isEmpty) gameList.appendChild(li("No games right now", "empty"))
      games.foreach(g => gameList.appendChild(gameItem(g.red, g.blue)))

    case ChatMessage(from, text) => appendChat(from, text)

    case Searching => setSearching("Searching for an opponent...")

    case PrivateCreated(code) =>
      val location = dom.window.location
      setSearching(s"Private game $code: waiting for your friend. Share this link:",
        Some(s"${location.origin}${location.pathname}?join=$code"))

    case SearchCancelled => setSearching("")

    case MatchStarted(_, you, red, blue, scenario, state) =>
      enterGame(isLocal = false)
      scenarioName = ScenarioRepository.byId(scenario).map(_.name).getOrElse("")
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
  ui.onEvent = {
    case GameEvent.Attacked(_, _, 2) => showToast("Attack succeeded: unit destroyed")
    case GameEvent.Attacked(_, _, 1) => showToast("Attack succeeded: unit must retreat")
    case GameEvent.Attacked(_, _, 3) => showToast("Attack succeeded: unit could not retreat and was removed")
    case GameEvent.Attacked(_, _, _) => showToast("Attack repelled")
    case GameEvent.ArsenalTaken(_, _) => showToast("Arsenal captured")
    case _ =>
  }
  endTurnBtn.onclick = (_: Event) => ui.endTurn()

  el[html.Button]("loginBtn").onclick = (_: Event) => login()
  nameInput.onkeydown = (e: KeyboardEvent) => if (e.key == "Enter") login()
  nameInput.value = stored("gow.name").getOrElse("")
  quickBtn.onclick = (_: Event) => client.send(QuickMatch)
  privateBtn.onclick = (_: Event) => client.send(CreatePrivate(scenarioPicker.value.id))
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
  private val scenarioPicker = new ScenarioPicker(el[html.Element]("scenarioPicker"), stored("gow.scenario"),
    onPreview = preview, onChange = s => storage.setItem("gow.scenario", s.id))
  preview(scenarioPicker.value)
  setSearching("")
  show(gameView, visible = false)
  setInterval(1000)(if (opponentDeadline.nonEmpty && inGame) renderGame())
}
