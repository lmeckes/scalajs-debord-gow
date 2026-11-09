package com.github.meckesl.ui

import com.github.meckesl.Point
import com.github.meckesl.repo.RuleRepository
import org.scalajs.dom
import org.scalajs.dom.{Touch, TouchEvent, html}

/** Sizes the board to its container and handles touch input: tap or drag units, pinch or double-tap to zoom,
  * drag empty ground to pan. Mouse input is wired separately and is not affected. */
class BoardView(wrap: html.Element, board: html.Element, canvases: Seq[html.Canvas], controller: UiController) {

  private var zoom = 1.0
  private var fitTile = 8.0
  private def maxZoom: Double = math.max(1.0, 56 / fitTile)

  def layout(): Unit = {
    fitTile = math.max(8, math.floor(4 * math.min(
      wrap.clientWidth.toDouble / RuleRepository.squareX,
      wrap.clientHeight.toDouble / RuleRepository.squareY)) / 4)
    zoom = zoom.max(1).min(maxZoom)
    val tile = if (zoom == 1) fitTile else math.floor(4 * fitTile * zoom) / 4
    val size = new Point(tile * RuleRepository.squareX, tile * RuleRepository.squareY)
    board.style.width = s"${size.x}px"
    board.style.height = s"${size.y}px"
    wrap.classList.toggle("zoomed", zoom > 1)
    controller.onResize(size)
  }

  /** Zooms so that the board point at fraction (fx, fy) of its size ends up under screen point (x, y). */
  private def zoomTo(z: Double, fx: Double, fy: Double, x: Double, y: Double): Unit = {
    zoom = z
    layout()
    val wr = wrap.getBoundingClientRect()
    wrap.scrollLeft = board.offsetLeft + fx * board.offsetWidth - (x - wr.left)
    wrap.scrollTop = board.offsetTop + fy * board.offsetHeight - (y - wr.top)
  }

  private def toggleZoom(x: Double, y: Double): Unit = {
    val br = board.getBoundingClientRect()
    val target = if (zoom > 1.01) 1.0 else math.min(maxZoom, math.max(2.0, 40 / fitTile))
    zoomTo(target, (x - br.left) / br.width, (y - br.top) / br.height, x, y)
  }

  private sealed trait Mode
  private case object Idle extends Mode
  private case object Tap extends Mode
  private case object UnitDrag extends Mode
  private case object Pan extends Mode
  private case object Pinch extends Mode
  private case object Done extends Mode
  private var mode: Mode = Idle

  private val moveThreshold = 10.0
  private var startX, startY, lastX, lastY = 0.0
  private var dragging = false
  private var lastTapTime = -1e9
  private var lastTapX, lastTapY = 0.0

  private var pinchD0, pinchM0x, pinchM0y, pinchM1x, pinchM1y, pinchLx, pinchLy, pinchW0, pinchH0 = 0.0
  private var pinchScale = 1.0

  private def moved(t: Touch): Boolean = math.hypot(t.clientX - startX, t.clientY - startY) > moveThreshold

  private def startPinch(a: Touch, b: Touch): Unit = {
    if (mode == UnitDrag) controller.touchCancel()
    hideLoupe()
    val br = board.getBoundingClientRect()
    pinchD0 = math.max(1, math.hypot(a.clientX - b.clientX, a.clientY - b.clientY))
    pinchM0x = (a.clientX + b.clientX) / 2
    pinchM0y = (a.clientY + b.clientY) / 2
    pinchM1x = pinchM0x
    pinchM1y = pinchM0y
    pinchLx = pinchM0x - br.left
    pinchLy = pinchM0y - br.top
    pinchW0 = br.width
    pinchH0 = br.height
    pinchScale = 1
    board.style.transformOrigin = "0 0"
    wrap.classList.add("zoomed")
    mode = Pinch
  }

  private def updatePinch(a: Touch, b: Touch): Unit = {
    val d = math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
    pinchScale = (zoom * d / pinchD0).max(1).min(maxZoom) / zoom
    pinchM1x = (a.clientX + b.clientX) / 2
    pinchM1y = (a.clientY + b.clientY) / 2
    val tx = pinchM1x - pinchM0x + pinchLx * (1 - pinchScale)
    val ty = pinchM1y - pinchM0y + pinchLy * (1 - pinchScale)
    board.style.transform = s"translate(${tx}px, ${ty}px) scale($pinchScale)"
  }

  private def endPinch(): Unit = {
    board.style.transform = ""
    zoomTo(zoom * pinchScale, pinchLx / pinchW0, pinchLy / pinchH0, pinchM1x, pinchM1y)
  }

  private def tap(x: Double, y: Double): Unit = {
    val now = dom.window.performance.now()
    if (now - lastTapTime < 300 && math.hypot(x - lastTapX, y - lastTapY) < 30) {
      lastTapTime = -1e9
      toggleZoom(x, y)
    } else {
      lastTapTime = now
      lastTapX = x
      lastTapY = y
      controller.touchTap(x, y)
    }
  }

  private def onTouchStart(e: TouchEvent): Unit = {
    e.preventDefault()
    val ts = e.touches
    if (ts.length >= 2 && mode != Pinch && mode != Done) startPinch(ts(0), ts(1))
    else if (ts.length == 1 && mode == Idle) {
      val t = ts(0)
      startX = t.clientX; startY = t.clientY
      lastX = startX; lastY = startY
      dragging = false
      mode = if (controller.touchDown(startX, startY)) UnitDrag else Tap
    }
  }

  private def onTouchMove(e: TouchEvent): Unit = {
    e.preventDefault()
    val ts = e.touches
    if (ts.length == 0) return
    val t = ts(0)
    mode match {
      case Pinch if ts.length >= 2 => updatePinch(ts(0), ts(1))
      case UnitDrag =>
        if (!dragging && moved(t)) dragging = true
        if (dragging) {
          controller.touchDrag(t.clientX, t.clientY)
          drawLoupe(t.clientX, t.clientY)
        }
      case Tap if moved(t) => mode = Pan
      case Pan =>
        wrap.scrollLeft -= t.clientX - lastX
        wrap.scrollTop -= t.clientY - lastY
      case _ =>
    }
    lastX = t.clientX
    lastY = t.clientY
  }

  private def onTouchEnd(e: TouchEvent): Unit = {
    e.preventDefault()
    val t = e.changedTouches(0)
    mode match {
      case Pinch =>
        endPinch()
        mode = Done
      case UnitDrag =>
        hideLoupe()
        if (dragging) controller.touchDrop() else tap(t.clientX, t.clientY)
      case Tap => tap(t.clientX, t.clientY)
      case _ =>
    }
    if (e.touches.length == 0) mode = Idle
    else if (mode != Pinch) mode = Done
  }

  private def onTouchCancel(e: TouchEvent): Unit = {
    hideLoupe()
    mode match {
      case UnitDrag => controller.touchCancel()
      case Pinch => endPinch()
      case _ =>
    }
    mode = if (e.touches.length == 0) Idle else Done
  }

  private val loupeSize = 120
  private val loupe = dom.document.createElement("canvas").asInstanceOf[html.Canvas]
  loupe.id = "loupe"
  dom.document.body.appendChild(loupe)
  private val boardImage = dom.document.createElement("img").asInstanceOf[html.Image]
  boardImage.src = "target/scala-2.13/classes/background.png"

  private def hideLoupe(): Unit = loupe.style.display = "none"

  /** Magnified view of the board around the finger, shown above it so the dragged unit stays visible. */
  private def drawLoupe(x: Double, y: Double): Unit = {
    val ratio = dom.window.devicePixelRatio
    val px = math.round(loupeSize * ratio).toInt
    if (loupe.width != px) {
      loupe.width = px
      loupe.height = px
    }
    val br = canvases.last.getBoundingClientRect()
    val mag = math.max(1.5, 44 / (br.width / RuleRepository.squareX))
    val src = loupeSize / mag
    val sx = x - br.left - src / 2
    val sy = y - br.top - src / 2
    val ctx = loupe.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.globalAlpha = 1
    ctx.fillStyle = "#2c3327"
    ctx.fillRect(0, 0, px, px)
    if (boardImage.complete && boardImage.naturalWidth > 0) {
      val k = math.max(br.width / boardImage.naturalWidth, br.height / boardImage.naturalHeight)
      val ox = (br.width - boardImage.naturalWidth * k) / 2
      val oy = (br.height - boardImage.naturalHeight * k) / 2
      ctx.drawImage(boardImage, (sx - ox) / k, (sy - oy) / k, src / k, src / k, 0, 0, px, px)
    }
    canvases.foreach { c =>
      val k = c.width / br.width
      ctx.globalAlpha = if (c.id == "comCanvas") 0.5 else 1
      ctx.drawImage(c, sx * k, sy * k, src * k, src * k, 0, 0, px, px)
    }
    val top = if (y - loupeSize - 48 < 4) y + 48 else y - loupeSize - 48
    val left = (x - loupeSize / 2).max(4).min(dom.window.innerWidth - loupeSize - 4)
    loupe.style.left = s"${left}px"
    loupe.style.top = s"${top}px"
    loupe.style.display = "block"
  }

  private val active = new dom.EventListenerOptions { passive = false }
  wrap.addEventListener("touchstart", (e: TouchEvent) => onTouchStart(e), active)
  wrap.addEventListener("touchmove", (e: TouchEvent) => onTouchMove(e), active)
  wrap.addEventListener("touchend", (e: TouchEvent) => onTouchEnd(e), active)
  wrap.addEventListener("touchcancel", (e: TouchEvent) => onTouchCancel(e), active)
}
