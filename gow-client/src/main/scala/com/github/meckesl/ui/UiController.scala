package com.github.meckesl.ui

import com.github.meckesl.io.Loader
import com.github.meckesl.repo.{RuleRepository, TileRepository}
import com.github.meckesl.{Board, Game, GameSnapshot, Point, Square}
import com.github.meckesl.protocol.{GameAction, GameEvent, GameState}
import com.github.meckesl.repo.PlayerRepository.Player
import com.github.meckesl.repo.TileRepository.VoidTile
import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag, HTMLAnchorElement, HTMLAudioElement, URL}
import org.scalajs.dom.html.Canvas

import scala.scalajs.js

case class UiController(var game: Game, backgroundCanvas: Canvas, comCanvas: Canvas, terrainCanvas: Canvas, unitCanvas: Canvas, overlayCanvas: Canvas, info: InfoPanel) {

  private val uiBackground = new UiLayer(backgroundCanvas)
  private val uiCom = new UiLayer(comCanvas)
  private val uiTerrain = new UiLayer(terrainCanvas)
  private val uiUnits = new UiLayer(unitCanvas)
  private val uiOverlay = new UiLayer(overlayCanvas)
  private val layers = Seq(uiBackground, uiCom, uiTerrain, uiUnits, uiOverlay)

  /** Which side the local user may currently play for, and where their actions go. */
  var controls: Player => Boolean = _ => false
  var onAction: GameAction => Unit = _ => ()
  var onStateChange: () => Unit = () => ()
  var onEvent: GameEvent => Unit = _ => ()
  var onBoardChange: () => Unit = () => ()

  /** Switches to an empty game on `board` (filled by the next `showState`) when it differs from the current one. */
  def setBoard(board: Board): Unit = if (board != game.board) {
    game = new Game(board, Seq.fill(board.size)(VoidTile))
    squareHover = null
    squareClicked = null
    squareSource = null
    animation = None
    info.clear()
    onBoardChange()
  }

  private def myTurn: Boolean = controls(game.turnPlayer)
  def canAct: Boolean = myTurn
  def endTurn(): Unit = if (myTurn) onAction(GameAction.EndTurn)

  private var squareHover: Square = _
  private var squareClicked: Square = _
  private var squareSource: Square = _

  def tileSize: Point = uiTerrain.tileSize

  private case class MoveAnimation(square: Int, from: Point, start: Double)
  private var animation: Option[MoveAnimation] = None
  private val animationMs = 240.0

  private val audioChannel1 = dom.document.createElement("audio").asInstanceOf[HTMLAudioElement]
  private val audioChannel2 = dom.document.createElement("audio").asInstanceOf[HTMLAudioElement]
  private val audioChannel3 = dom.document.createElement("audio").asInstanceOf[HTMLAudioElement]
  Set(audioChannel1, audioChannel2, audioChannel3).foreach(_.volume=0.3)

  audioChannel3.src = Loader.getSoundUrl("start")
  audioChannel3.play()

  info.clear()

  private def getGameSquare(e: dom.MouseEvent): Square = {
    val rect = overlayCanvas.getBoundingClientRect()
    val x = ((e.clientX - rect.left) / tileSize.x).toInt.max(0).min(game.board.width - 1)
    val y = ((e.clientY - rect.top) / tileSize.y).toInt.max(0).min(game.board.height - 1)
    game.gameSquares(new Point(x, y).toLinear(game.board.width))
  }

  private def now: Double = dom.window.performance.now()

  private def unitPosition(sq: Square, time: Double): Point = animation match {
    case Some(a) if a.square == sq.index =>
      val t = ((time - a.start) / animationMs).min(1.0)
      val eased = 1 - math.pow(1 - t, 3)
      a.from + (sq.coords - a.from) * eased
    case _ => sq.coords
  }

  private def drawUnits(): Unit = {
    val time = now
    uiUnits.clearLayer()
    TileRepository.units.foreach(u => {
      Loader.loadTileAsync(u, image => {
        game.gameSquares.filter(_.unit.equals(u)).foreach(sq => {
          uiUnits.tileUnit(sq, image, unitPosition(sq, time))
        })
      })
    })
  }

  private def animate(): Unit = {
    drawUnits()
    animation.foreach { a =>
      if (now - a.start < animationMs) dom.window.requestAnimationFrame(_ => animate())
      else {
        animation = None
        drawUnits()
      }
    }
  }

  private def redraw(): Unit = {
    drawUnits()
    uiCom.clearLayer()
    game.gameSquares.foreach(sq => {
      uiCom.tileCommunication(sq)
    })
  }

  private def play(channel: HTMLAudioElement, url: String): Unit = {
    channel.src = url
    channel.play()
  }

  def showState(state: GameState, event: Option[GameEvent]): Unit = {
    val previousTurn = game.turnPlayer
    GameSnapshot.applyTo(state, game)
    squareSource = null
    squareClicked = null
    uiOverlay.clearLayer()
    event.foreach {
      case GameEvent.Moved(from, to) =>
        animation = Some(MoveAnimation(to, game.gameSquares(from).coords, now))
        play(audioChannel2, Loader.getSoundUrl(game.gameSquares(to), "move"))
      case GameEvent.Attacked(from, _, result) if result > 0 => play(audioChannel2, Loader.getSoundUrl(game.gameSquares(from), "attack"))
      case GameEvent.ArsenalTaken(from, to) =>
        animation = Some(MoveAnimation(to, game.gameSquares(from).coords, now))
        play(audioChannel2, Loader.getSoundUrl(game.gameSquares(to), "attack"))
      case _ =>
    }
    redraw()
    if (animation.nonEmpty) animate()
    if (event.nonEmpty && game.turnPlayer != previousTurn)
      play(audioChannel3, Loader.getSoundUrl("nextTurn"))
    Option(squareHover).foreach { h =>
      squareHover = null
      hover(h)
    }
    event.foreach(onEvent)
    onStateChange()
  }

  /** Lays the board out at `s` CSS pixels. */
  def onResize(s: Point): Unit = {
    val ratio = dom.window.devicePixelRatio
    layers.foreach(_.resize(s, ratio, new Point(game.board.width, game.board.height)))

    uiBackground.boardGrid()

    uiTerrain.clearLayer()
    TileRepository.terrains.filterNot(_.equals(VoidTile)).foreach(t => {
      Loader.loadTileAsync(t, image => {
        game.gameSquares.filter(_.terrain.equals(t)).foreach(sq => {
          uiTerrain.tileTerrain(sq, image)
        })
      })
    })

    redraw()

    if (null != squareClicked)
      uiOverlay.tileUnitHighlight(squareClicked)
  }

  private def showInfo(sq: Square): Unit = {
    if (sq.unit != VoidTile || sq.terrain != VoidTile) info.showSquare(sq)
    else if (null != squareClicked) info.showSquare(squareClicked)
    else info.clear()
  }

  private def canGrab(sq: Square): Boolean =
    myTurn && (sq.canMove || (sq.canStartAttack && sq.targetsInAttackRange.nonEmpty))

  private def showMoveHints(sq: Square): Unit = {
    if (myTurn && sq.canMove)
      sq.inRange(sq.unit.speed).foreach { dest =>
        if (sq.canMoveTo(dest)) uiOverlay.moveHint(dest, Color.Move)
        else if (sq.canTakeArsenal(dest)) uiOverlay.moveHint(dest, Color.Arsenal)
      }
  }

  private def hover(sq: Square): Unit = {
    uiOverlay.clearLayer()
    if (null != squareClicked && squareClicked != sq)
      uiOverlay.tileUnitHighlight(squareClicked)
    if (sq.isCurrentTurn) {
      uiOverlay.tileUnitHighlight(sq)
      sq.alliesInRange.foreach(uiOverlay.tileHighlight(_, Color.fromPlayer(sq.unit.player, 0.18)))
      if (sq.canStartAttack) sq.targetsInAttackRange.foreach(uiOverlay.targetHint)
      showMoveHints(sq)
    }
    showInfo(sq)
    squareHover = sq
  }

  private def drag(sq: Square): Unit = {
    uiOverlay.clearLayer()
    uiOverlay.tileUnitHighlight(squareSource)
    showMoveHints(squareSource)
    if (squareSource.canAttack(sq)) {
      if (sq != squareHover)
        play(audioChannel2, Loader.getSoundUrl(sq, "target"))
      sq.canBeTargetOf.foreach(uiOverlay.tileHighlight(_, Color.fromPlayer(game.turnPlayer, 0.22)))
      sq.alliesInRange.foreach(uiOverlay.tileHighlight(_, Color.fromPlayer(sq.unit.player, 0.22)))
      uiOverlay.targetHint(sq)
      info.showAttack(sq)
    } else
      info.showSquare(squareSource)
    uiOverlay.drawActionArrow(squareSource, sq)
    squareHover = sq
  }

  def onMousemove(e: dom.MouseEvent): Unit = {
    val hoverSq = getGameSquare(e)
    overlayCanvas.style.cursor =
      if (null != squareSource) "grabbing"
      else if (canGrab(hoverSq)) "grab"
      else "default"

    if (null == squareSource || squareSource == hoverSq) {
      if (hoverSq != squareHover)
        hover(hoverSq)
    } else
      drag(hoverSq)
  }

  def onMouseleave(): Unit = {
    if (null == squareSource) {
      squareHover = null
      uiOverlay.clearLayer()
      if (null != squareClicked) {
        uiOverlay.tileUnitHighlight(squareClicked)
        info.showSquare(squareClicked)
      } else info.clear()
    }
  }

  def onClick(e: dom.MouseEvent): Unit = {
    if (null != squareHover && squareHover.isCurrentTurn) {
      squareClicked = squareHover
      uiOverlay.tileUnitHighlight(squareClicked)
      info.showSquare(squareClicked)
    }
  }

  def onMouseup(e: dom.MouseEvent): Unit = {
    val source = squareSource
    squareSource = null
    if (null != source && null != squareHover && myTurn) {
      if (source.canMoveTo(squareHover))
        onAction(GameAction.Move(source.index, squareHover.index))
      else if (source.canTakeArsenal(squareHover))
        onAction(GameAction.TakeArsenal(source.index, squareHover.index))
      else if (source.canAttack(squareHover))
        onAction(GameAction.Attack(source.index, squareHover.index))
    }
    if (null != source) {
      overlayCanvas.style.cursor = "default"
      Option(squareHover).foreach { h =>
        squareHover = null
        hover(h)
      }
    }
  }

  def onMousedown(e: dom.MouseEvent): Unit = {
    val curSq = getGameSquare(e)
    if (canGrab(curSq)) {
      audioChannel1.src = Loader.getSoundUrl(curSq, "select")
      audioChannel1.play()
      squareSource = curSq
      overlayCanvas.style.cursor = "grabbing"
      info.showSquare(squareSource)
    }
    else
      squareSource = null
  }

  private def squareAt(x: Double, y: Double): Option[Square] = {
    val rect = overlayCanvas.getBoundingClientRect()
    val cx = math.floor((x - rect.left) / tileSize.x).toInt
    val cy = math.floor((y - rect.top) / tileSize.y).toInt
    if (cx < 0 || cy < 0 || cx >= game.board.width || cy >= game.board.height) None
    else Some(game.gameSquares(new Point(cx, cy).toLinear(game.board.width)))
  }

  private def act(source: Square, target: Square): Boolean = {
    val action =
      if (!myTurn) None
      else if (source.canMoveTo(target)) Some(GameAction.Move(source.index, target.index))
      else if (source.canTakeArsenal(target)) Some(GameAction.TakeArsenal(source.index, target.index))
      else if (source.canAttack(target)) Some(GameAction.Attack(source.index, target.index))
      else None
    action.foreach { a =>
      squareClicked = null
      squareHover = null
      uiOverlay.clearLayer()
      info.clear()
      onAction(a)
    }
    action.nonEmpty
  }

  private def select(sq: Square): Unit = {
    squareClicked = sq
    hover(sq)
  }

  private def deselect(): Unit = {
    squareClicked = null
    squareHover = null
    uiOverlay.clearLayer()
  }

  /** Touch: a finger lands on the board. Returns true if it picked up one of the player's units. */
  def touchDown(x: Double, y: Double): Boolean = squareAt(x, y) match {
    case Some(sq) if canGrab(sq) =>
      play(audioChannel1, Loader.getSoundUrl(sq, "select"))
      hover(sq)
      squareSource = sq
      true
    case _ => false
  }

  /** Touch: the picked-up unit is dragged over the board. */
  def touchDrag(x: Double, y: Double): Unit =
    if (null != squareSource) squareAt(x, y).foreach { sq =>
      if (sq != squareHover) {
        if (sq == squareSource) hover(sq) else drag(sq)
      }
    }

  /** Touch: the dragged unit is released; if the drop isn't a legal action it stays selected. */
  def touchDrop(): Unit = {
    val source = squareSource
    squareSource = null
    if (null != source && !(null != squareHover && squareHover != source && act(source, squareHover)))
      select(source)
  }

  /** Touch: tap a unit to select it, then tap a destination or target to act. */
  def touchTap(x: Double, y: Double): Unit = {
    squareSource = null
    val selected = squareClicked
    squareAt(x, y) match {
      case None => deselect(); info.clear()
      case Some(sq) if null != selected && sq != selected && act(selected, sq) =>
      case Some(sq) if sq.isCurrentTurn && sq != selected => select(sq)
      case Some(sq) => deselect(); showInfo(sq)
    }
  }

  /** Touch: the gesture turned into a zoom or was interrupted; drop any unit being dragged. */
  def touchCancel(): Unit = if (null != squareSource) {
    val source = squareSource
    squareSource = null
    select(source)
  }

  def onKeydown(e: dom.KeyboardEvent): Unit = {
    val nextTurn = dom.KeyCode.Space
    val downloadGame = dom.KeyCode.D
    e.keyCode match {
      case `nextTurn` if myTurn =>
        e.preventDefault()
        onAction(GameAction.EndTurn)
      case `downloadGame` =>
        downloadGameFile()
      case _ =>
    }
  }

  private def downloadGameFile(): Unit = {
    def doc =
      game.gameSquares
        .map(sq => if (!sq.unit.equals(VoidTile)) sq.unit.char else sq.terrain.char)
        .grouped(game.board.width)
        .map(_.mkString(" "))
        .mkString("\n")
    val a = dom.document.createElement("a").asInstanceOf[HTMLAnchorElement]
    val pb = new BlobPropertyBag{}
    pb.`type`="text/plain"
    val blob = new Blob(js.Array(doc), pb)
    val url = URL.createObjectURL(blob)
    a.href = url
    a.setAttribute("download", "game.gow")
    a.style.display = "none"
    dom.document.body.appendChild(a)
    a.click()
    dom.document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

}
