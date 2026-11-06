package com.github.meckesl

import com.github.meckesl.io.Loader
import com.github.meckesl.repo.RuleRepository
import com.github.meckesl.ui.{InfoPanel, LobbyView, UiController}
import com.github.meckesl.{Game, Point}
import org.scalajs.dom._
import org.scalajs.dom.document._
import org.scalajs.dom.html

import scala.concurrent.ExecutionContext.Implicits.global

object App {

  def main(args: Array[String]): Unit = {

    def getCanvas(c: String) = getElementById(c).asInstanceOf[html.Canvas]
    val backgroundCanvas = getCanvas("backgroundCanvas")
    val comCanvas = getCanvas("comCanvas")
    val terrainCanvas = getCanvas("terrainCanvas")
    val unitCanvas = getCanvas("unitCanvas")
    val overlayCanvas = getCanvas("overlayCanvas")
    val board = getElementById("board").asInstanceOf[html.Div]
    val boardWrap = getElementById("boardWrap").asInstanceOf[html.Element]

    Loader.getStartingGamePosition("init.board", "init.units", 25).foreach {
      _ =>
        val game = new Game
        val info = new InfoPanel(getElementById("infoPanel").asInstanceOf[html.Element])
        val uiController = UiController(game, backgroundCanvas, comCanvas, terrainCanvas, unitCanvas, overlayCanvas, info)
        import scala.scalajs.js.timers._
        // Square tiles, as large as the space next to the side panel allows.
        def layout(): Unit = {
          val margin = 2
          val tile = math.max(8, math.floor(4 * math.min(
            (boardWrap.clientWidth - margin * 2).toDouble / RuleRepository.squareX,
            (boardWrap.clientHeight - margin * 2).toDouble / RuleRepository.squareY)) / 4)
          val size = new Point(tile * RuleRepository.squareX, tile * RuleRepository.squareY)
          board.style.width = s"${size.x}px"
          board.style.height = s"${size.y}px"
          uiController.onResize(size)
        }
        layout()
        var handle: SetTimeoutHandle = null
        window.onresize = (_: Event) => {
          clearTimeout(handle)
          handle = setTimeout(100)(layout())
        }
        overlayCanvas.onmousemove = (e: MouseEvent) => { uiController onMousemove e }
        overlayCanvas.onclick = (e: MouseEvent) => { uiController onClick e }
        overlayCanvas.onmouseup = (e: MouseEvent) => { uiController onMouseup e }
        overlayCanvas.onmousedown = (e: MouseEvent) => { uiController onMousedown e }
        overlayCanvas.onmouseleave = (_: MouseEvent) => { uiController.onMouseleave() }
        window.onkeydown = (e: KeyboardEvent) => {
          if (e.target.asInstanceOf[Element].tagName != "INPUT") uiController onKeydown e
        }
        new LobbyView(uiController, game)

    }
  }

}
