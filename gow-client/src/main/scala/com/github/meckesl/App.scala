package com.github.meckesl

import com.github.meckesl.io.Loader
import com.github.meckesl.ui.{LobbyView, UiController}
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
    val interfaceCanvas = getCanvas("interfaceCanvas")

    Loader.getStartingGamePosition("init.board", "init.units", 25).foreach {
      _ =>
        val game = new Game
        val uiController = UiController(game, backgroundCanvas, comCanvas, terrainCanvas, unitCanvas, overlayCanvas, interfaceCanvas)
        import scala.scalajs.js.timers._
        uiController.onResize(new Point(window.innerWidth, window.innerHeight))
        var handle: SetTimeoutHandle = null
        window.onresize = (_: Event) => {
          clearTimeout(handle)
          handle = setTimeout(200) {
            uiController.onResize(new Point(window.innerWidth, window.innerHeight))
          }
        }
        overlayCanvas.onmousemove = (e: MouseEvent) => { uiController onMousemove e }
        overlayCanvas.onclick = (e: MouseEvent) => { uiController onClick e }
        overlayCanvas.onmouseup = (e: MouseEvent) => { uiController onMouseup e }
        overlayCanvas.onmousedown = (e: MouseEvent) => { uiController onMousedown e }
        window.onkeydown = (e: KeyboardEvent) => {
          if (e.target.asInstanceOf[Element].tagName != "INPUT") uiController onKeydown e
        }
        new LobbyView(uiController, game)

    }
  }

}
